package features.core.powerfilter

import framework.ReposeValveTest
import framework.category.Smoke
import org.junit.experimental.categories.Category
import org.rackspace.deproxy.Deproxy
import org.rackspace.deproxy.MessageChain

class ApiValidatorRunSmokeTest extends ReposeValveTest {


    def setupSpec() {
        deproxy = new Deproxy()
        deproxy.addEndpoint(properties.targetPort)

        def params = properties.getDefaultTemplateParams()
        repose.configurationProvider.applyConfigs("common", params)
        repose.configurationProvider.applyConfigs("features/core/smoke", params)
        //Waiting on responses, rather than the buggy JMX
//        repose.start(killOthersBeforeStarting: false,
//                waitOnJmxAfterStarting: false)
//        repose.waitForNon500FromUrl(properties.reposeEndpoint)
        repose.start()
    }


    def cleanup() {
        if (repose) {
            repose.stop()
        }
        if (deproxy) {
            deproxy.shutdown()
        }
    }

    @Category(Smoke)
    def "when request is sent check to make sure it goes through ip-identity and API-Validator filters"() {

        when:

        MessageChain mc1 =  deproxy.makeRequest([url: reposeEndpoint + "/resource", method: "get",headers:['X-Roles':'role-1','x-trace-request': 'true']])

        then:
        mc1.receivedResponse.getHeaders().names.contains("X-api-validator-Time")
        mc1.receivedResponse.getHeaders().names.contains("X-ip-identity-Time")

     }



}
