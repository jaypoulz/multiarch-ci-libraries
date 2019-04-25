import org.junit.Test
import org.junit.Before
import com.redhat.ci.provisioner.ProvisioningConfig

/**
 * Tests the API wrapper.
 */
class MAQEAPITest extends PipelineTestScript {

    @Before
    void init() {
        reset()
    }

    @Test
    void canCallAPIVersion1Methods() {
        ProvisioningConfig config = MAQEAPI.v1.getProvisioningConfig(this)
        assert(config != null)
    }

    @Test
    void canRunAPIVersion2Methods() {
        MAQEAPI.v2.validate(null)
        MAQEAPI.v2.provision(null)
        MAQEAPI.v2.configure(null)
        MAQEAPI.v2.execute(null)
        MAQEAPI.v2.archive(null)
        MAQEAPI.v2.teardown(null)
        MAQEAPI.v2.report(null)
        assertNoExceptions()
    }
}
