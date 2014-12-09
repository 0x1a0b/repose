package org.openrepose.core.services.reporting;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.openrepose.core.services.config.ConfigurationService;
import org.openrepose.core.services.reporting.destinations.DestinationInfo;
import org.openrepose.core.services.reporting.impl.ReportingServiceImpl;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

@RunWith(Enclosed.class)
public class ReportingServiceImplTest {

    public static class WhenResetting {

        private static final int REFRESH_SECONDS = 2;
        private ReportingService reportingService;
        private List<String> destinationIds = new ArrayList<String>();

        @Before
        public void setup() {
            destinationIds.add("id_1");
            destinationIds.add("id_2");
            destinationIds.add("id_7");

            reportingService = new ReportingServiceImpl(mock(ConfigurationService.class));
            reportingService.updateConfiguration(destinationIds, REFRESH_SECONDS);
        }

        @Test
        public void shouldResetValuesEvery2Seconds() throws InterruptedException {
            for (int i = 0; i < 5; i++) {
                reportingService.incrementRequestCount("id_7");
            }

            assertEquals(5, reportingService.getDestinationInfo("id_7").getTotalRequests());

            // sleep for three seconds
            Thread.sleep(3*1000);

            assertEquals(0, reportingService.getDestinationInfo("id_7").getTotalRequests());
        }
        
        @Test
        public void shouldReturnDestinationList(){
           
           List<DestinationInfo> dst = reportingService.getDestinations();
           
           assertEquals(3, dst.size());
        }
        
        @Test
        public void shouldRecordServiceResponse(){
           
           Long responseTime = new Long("200");
           reportingService.recordServiceResponse("id_1", 202, responseTime);
           DestinationInfo dstInfo = reportingService.getDestinationInfo("id_1");
           assertEquals(1, dstInfo.getTotalStatusCode(202));
        }
    }
}
