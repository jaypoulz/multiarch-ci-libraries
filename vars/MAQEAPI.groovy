import com.redhat.ci.api.V2

/**
 * A wrapper variable that contains pointers to instances of the existing API.
 */
class MAQEAPI {
    static TestUtils v1 = new TestUtils()
    static V2 v2 = new V2()
}
