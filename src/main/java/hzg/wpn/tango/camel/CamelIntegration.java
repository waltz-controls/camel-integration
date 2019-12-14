package hzg.wpn.tango.camel;

import fr.esrf.Tango.DevFailed;
import hzg.wpn.xenv.ResourceManager;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.slf4j.MDC;
import org.tango.DeviceState;
import org.tango.server.ServerManager;
import org.tango.server.annotation.*;
import org.tango.server.device.DeviceManager;
import org.tango.utils.DevFailedUtils;

import java.util.List;

/**
 * @author Igor Khokhriakov <igor.khokhriakov@hzg.de>
 * @since 2/12/16
 */
@Device
public class CamelIntegration {
    @DeviceManagement
    private DeviceManager deviceManager;
    @State(isPolled = true, pollingPeriod = 3000)
    private volatile DeviceState state;
    @Status(isPolled = true, pollingPeriod = 3000)
    private volatile String status;

    public void setDeviceManager(DeviceManager deviceManager) {
        this.deviceManager = deviceManager;
    }

    private CamelContext camelContext;

    @Init
    public void init() throws Exception {

        SimpleRegistry registry = new SimpleRegistry();


        camelContext = new DefaultCamelContext(registry);


        RoutesDefinition routeDefinition = camelContext.loadRoutesDefinition(
                ResourceManager.loadResource("etc/CamelIntegration","routes.xml"));

        routeDefinition.getRoutes().forEach(routeDefinition1 -> routeDefinition1.onException(DevFailed.class)
                .process(exchange -> {
                    MDC.setContextMap(deviceManager.getDevice().getMdcContextMap());
                    DevFailed exception = (DevFailed) exchange.getProperty(Exchange.EXCEPTION_CAUGHT);
                    deviceManager.pushStateChangeEvent(DeviceState.ALARM);
                    deviceManager.pushStatusChangeEvent(DevFailedUtils.toString(exception));
                }));


        List<RouteDefinition> routes = routeDefinition.getRoutes();
        camelContext.addRouteDefinitions(routes);
        deviceManager.pushStateChangeEvent(DeviceState.ON);
    }

    @Attribute
    public String[] getRouteDefinitions(){
        return
                camelContext.getRouteDefinitions().stream().map(RouteDefinition::getId).toArray(String[]::new);
    }

    @Attribute
    public String[] getRoutes(){
        return
                camelContext.getRoutes().stream().map(Route::getId).toArray(String[]::new);
    }

    @Command
    public void start() throws Exception {
        camelContext.start();
        deviceManager.pushStateChangeEvent(DeviceState.RUNNING);
        deviceManager.pushStatusChangeEvent("STARTED");
    }

    @Command
    public void stop() throws Exception {
        camelContext.stop();
        deviceManager.pushStateChangeEvent(DeviceState.ON);
        deviceManager.pushStatusChangeEvent("STOPPED");
    }

    @Delete
    public void delete() throws Exception {
        stop();
        deviceManager.pushStateChangeEvent(DeviceState.OFF);
    }

    public static void main(String[] args) {
        ServerManager.getInstance().start(args, CamelIntegration.class);
    }

    public DeviceState getState() {
        return state;
    }

    public void setState(DeviceState state) {
        this.state = state;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = String.format("%d: %s", System.currentTimeMillis(), status);
    }
}
