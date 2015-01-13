package features.filters.clientauthn.multitenantsheaders
import framework.ReposeValveTest
import framework.mocks.MockIdentityService
import org.joda.time.DateTime
import org.rackspace.deproxy.Deproxy
import org.rackspace.deproxy.MessageChain
import spock.lang.Unroll
/**
 * Created by jennyvo on 8/19/14.
 */
class MultiTenantedCheckTest extends ReposeValveTest {

    def static originEndpoint
    def static identityEndpoint

    def static MockIdentityService fakeIdentityService

    def setupSpec() {

        deproxy = new Deproxy()

        def params = properties.defaultTemplateParams
        repose.configurationProvider.applyConfigs("common", params)
        repose.configurationProvider.applyConfigs("features/filters/clientauthn/common", params)
        repose.configurationProvider.applyConfigs("features/filters/clientauthn/multitenantheader", params)
        repose.start()

        originEndpoint = deproxy.addEndpoint(properties.targetPort, 'origin service')
        fakeIdentityService = new MockIdentityService(properties.identityPort, properties.targetPort)
        identityEndpoint = deproxy.addEndpoint(properties.identityPort,
                'identity service', null, fakeIdentityService.handler)


    }

    def cleanupSpec() {
        deproxy.shutdown()

        repose.stop()
    }

    def setup(){
        fakeIdentityService.resetHandlers()
    }

    @Unroll ("when passing #requestTenant with setting #defaultTenant #serviceRespCode")
    def "When authenticate user with tenanted client-mapping matching more than one from tenant list" () {
        given:
        fakeIdentityService.with {
            client_token = clientToken
            tokenExpiresAt = (new DateTime()).plusDays(1);
            client_tenant = defaultTenant
            client_tenant_file = "nast-id"
            service_admin_role = "not-admin"
        }

        when: "User passes a request through repose with $requestTenant"
        MessageChain mc = deproxy.makeRequest(
                url: "$reposeEndpoint/servers/$requestTenant",
                method: 'GET',
                headers: ['content-type': 'application/json', 'X-Auth-Token': fakeIdentityService.client_token])

        then: "Everything gets passed as is to the origin service (no matter the user)"
        mc.receivedResponse.code == serviceRespCode
        if (serviceRespCode != "200")
            assert mc.handlings.size() == 0
        else {
            assert mc.handlings.size() == 1
            assert mc.handlings[0].request.headers.getFirstValue('x-tenant-id') == requestTenant
        }

        where:
        defaultTenant   | requestTenant         | authResponseCode  |clientToken        |serviceRespCode
        "123456"        | "123456"              | "200"             |UUID.randomUUID()  | "200"
        "123456"        | "nast-id"             | "200"             |UUID.randomUUID()  | "200"
        "123456"        | "no-a-nast-id"        | "200"             |UUID.randomUUID()  | "401"
        "900000"        | "nast-id"             | "200"             |UUID.randomUUID()  | "200"
        "nast-id"       | "nast-id"             | "200"             |UUID.randomUUID()  | "200"
        "900000"        | "900000"              | "200"             |''                 | "401"
    }
}
