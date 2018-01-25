package com.redhat.multiarch.ci.provisioner

class Provisioner {
  def script
  ProvisioningConfig config

  Provisioner(def script, ProvisioningConfig config) {
    this.script = script
    this.config = config
  }

  /**
   * Attempts to provision a multi-arch host.
   *
   * @param arch String representing architecture of the host to provision.
   */
  Host provision(String arch) {
    Host host = new Host(
      arch: arch,
      target: 'jenkins-slave',
      name: "${arch}-slave"
    )

    try {
      script.withCredentials([file(credentialsId: config.KEYTABCREDENTIALID,
                                   variable: 'KEYTAB')]) {
        script.sh "kinit ${config.krbPrincipal} -k -t ${KEYTAB}"

        // Test to make sure we can authenticate.
        script.sh 'bkr whoami'
      }

      if (config.provisioningRepoUrl != null) {
        // Get linchpin workspace
        script.git(url: config.provisioningRepoUrl, branch: config.provisioningRepoRef)
      } else {
        script.checkout scm
      }

      // Attempt provisioning
      script.sh "linchpin --workspace ${config.provisioningWorkspaceDir} --template-data '{ arch: ${host.arch}, job_group: ${config.jobgroup} }' --verbose up ${host.target}"

      // We need to scan for inventory file. Please see the following for reasoning:
      // - https://github.com/CentOS-PaaS-SIG/linchpin/issues/430
      // Possible solutions to not require the scan:
      // - https://github.com/CentOS-PaaS-SIG/linchpin/issues/421
      // - overriding [evars] section and specifying inventory_file
      //
      host.inventory = sh (returnStdout: true, script: """
            readlink -f ${config.provisioningWorkspaceDir}/inventories/*.inventory
            """).trim()
      host.provisioned = true

      if (config.runOnSlave) {
        script.withCredentials([
          [
            $class: 'UsernamePasswordMultiBinding',
            credentialsId: config.JENKINSSLAVECREDENTIALID,
            usernameVariable: 'JENKINS_SLAVE_USERNAME',
            passwordVariable: 'JENKINS_SLAVE_PASSWORD'
          ]
        ]) {
          def extraVars = "'{" +
            "'rpm_key_imports':[]," +
            "'jenkins_master_repositories':[]," +
            "'jenkins_master_download_repositories':[]," +
            "'jslave_name':'${host.name}'," +
            "'jslave_label':'${host.name}'," +
            "'arch':'${host.arch}'," +
            "'jenkins_master_url':'${config.JENKINS_MASTER_URL}'," +
            "'jenkins_slave_username':'${JENKINS_SLAVE_USERNAME}'," +
            "'jenkins_slave_password':'${JENKINS_SLAVE_PASSWORD}'," +
            "'jswarm_extra_args':'${config.JSWARM_EXTRA_ARGS}'" +
            "}'"

          script.sh "cinch ${host.inventory} --extra-vars ${extraVars}"
          host.connectedToMaster = true
        }
      } else {
        script.withCredentials([file(credentialsId: config.SSHPRIVKEYCREDENTIALID,
                                     variable: 'SSHPRIVKEY'),
                                file(credentialsId: config.SSHPUBKEYCREDENTIALID,
                                     variable: 'SSHPUBKEY')])
        {
          script.env.HOME = "/home/jenkins"
          script.sh """
            mkdir -p ~/.ssh
            cp ${SSHPRIVKEY} ~/.ssh/id_rsa
            cp ${SSHPUBKEY} ~/.ssh/id_rsa.pub
            chmod 600 ~/.ssh/id_rsa
            chmod 644 ~/.ssh/id_rsa.pub
          """
        }
      }
      if (config.installAnsible) {
        script.node (host.name) {
          script.sh 'sudo yum install python-devel openssl-devel libffi-devel -y'
          script.sh 'sudo pip install --upgrade pip; sudo pip install --upgrade setuptools; sudo pip install --upgrade ansible'
        }
        host.ansibleInstalled = true
      }
    } catch (e) {
      script.echo e.getMessage()
      host.error = e.getMessage()
    }

    host
  }

  /**
   * Runs a teardown for provisioned host.
   *
   * @param host Provisioned host to be torn down.
   * @param arch String specifying the arch to run tests on.
   */
  def teardown(Host host, String arch) {
    // Prepare the cinch teardown inventory
    if (!host || !host.provisioned) {
      // The provisioning job did not successfully provision a machine, so there is nothing to teardown
      script.currentBuild.result = 'SUCCESS'
      return
    }

    // Preform the actual teardown
    try {
      script.sh "teardown workspace/inventories/${host.target}.inventory"
    } catch (e) {
      script.echo e
    }

    try {
      script.sh "linchpin --workspace workspace --template-data \'{ arch: $arch, job_group: $config.jobgroup }\' --verbose destroy ${host.target}"
    } catch (e) {
      script.echo e

      if (host.error) {
        script.currentBuild.result = 'FAILURE'
      }
    }
  }
}
