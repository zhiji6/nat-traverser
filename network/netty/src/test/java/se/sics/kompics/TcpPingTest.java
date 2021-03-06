package se.sics.kompics;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.gvod.address.Address;
import se.sics.gvod.common.Utility;
import se.sics.gvod.common.UtilityVod;
import se.sics.gvod.common.VodDescriptor;
import se.sics.gvod.config.BaseCommandLineConfig;
import se.sics.gvod.config.VodConfig;
import se.sics.gvod.hp.msgs.TConnectionMsg;
import se.sics.gvod.net.*;
import se.sics.gvod.net.events.*;
import se.sics.gvod.net.events.PortBindResponse.Status;
import se.sics.gvod.timer.ScheduleTimeout;
import se.sics.gvod.timer.Timer;
import se.sics.gvod.timer.UUID;
import se.sics.gvod.timer.java.JavaTimer;
import se.sics.kompics.nat.utils.getip.ResolveIp;
import se.sics.kompics.nat.utils.getip.ResolveIpPort;
import se.sics.kompics.nat.utils.getip.events.GetIpRequest;
import se.sics.kompics.nat.utils.getip.events.GetIpResponse;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.Semaphore;

/**
 * Simple ping pong test for UDP.
 *
 * @author Steffen Grohsschmiedt
 */
public class TcpPingTest extends TestCase {

    private static final Logger logger = LoggerFactory.getLogger(TcpPingTest.class);
    private boolean testStatus = true;

    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public TcpPingTest(String testName) {
        super(testName);
        System.setProperty("java.net.preferIPv4Stack", "true");
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite() {
        return new TestSuite(TcpPingTest.class);
    }

    public static void setTestObj(TcpPingTest testObj) {
        TestStClientComponent.testObj = testObj;
    }

    public static class TestStClientComponent extends ComponentDefinition {

        private Component client;
        private Component server;
        private Component timer;
        private Component resolveIp;
        private static TcpPingTest testObj = null;
        private VodAddress clientAddr;
        private VodAddress serverAddr;
        private Utility utility = new UtilityVod(10, 200, 15);
        private VodDescriptor nodeDesc;
        private List<VodDescriptor> nodes;

        public TestStClientComponent() {
            timer = create(JavaTimer.class, Init.NONE);
            client = create(NettyNetwork.class, 
                    new NettyInit(132, true, BaseMsgFrameDecoder.class));
            server = create(NettyNetwork.class, 
                    new NettyInit(132, true, BaseMsgFrameDecoder.class));
            resolveIp = create(ResolveIp.class, Init.NONE);

            subscribe(handleStart, control);
            subscribe(handleMsgTimeout, timer.getPositive(Timer.class));
            subscribe(handlePong, client.getPositive(VodNetwork.class));
            subscribe(handlePortBindResponse, client.getPositive(NatNetworkControl.class));
            subscribe(handlePortBindResponse, server.getPositive(NatNetworkControl.class));
            subscribe(handlePing, server.getPositive(VodNetwork.class));
            subscribe(handleGetIpResponse, resolveIp.getPositive(ResolveIpPort.class));
            subscribe(handleCloseConnectionResponse, client.getPositive(NatNetworkControl.class));
            subscribe(handlePortDeletionResponse, server.getPositive(NatNetworkControl.class));
        }

        public Handler<Start> handleStart = new Handler<Start>() {
            public void handle(Start event) {
                trigger(new GetIpRequest(false, EnumSet.of(
                        GetIpRequest.NetworkInterfacesMask.IGNORE_LOCAL_ADDRESSES,
                        GetIpRequest.NetworkInterfacesMask.IGNORE_TEN_DOT_PRIVATE)),
                        resolveIp.getPositive(ResolveIpPort.class));

                logger.info("Starting");
                ScheduleTimeout st = new ScheduleTimeout(30 * 1000);
                MsgTimeout mt = new MsgTimeout(st);
                st.setTimeoutEvent(mt);
                trigger(st, timer.getPositive(Timer.class));
            }
        };
        public Handler<GetIpResponse> handleGetIpResponse = new Handler<GetIpResponse>() {
            @Override
            public void handle(GetIpResponse event) {
                InetAddress ip = null;
                int clientPort = 54644;
                int serverPort = 54645;

                try {
                    ip = InetAddress.getLocalHost();
                } catch (UnknownHostException ex) {
                    logger.error("UnknownHostException");
                    fail();
                }
                Address cAddr = new Address(ip, clientPort, 0);
                Address sAddr = new Address(ip, serverPort, 1);


                logger.info("Server: " + cAddr);
                logger.info("Client: " + cAddr);


                clientAddr = new VodAddress(cAddr, VodConfig.SYSTEM_OVERLAY_ID);
                serverAddr = new VodAddress(sAddr, VodConfig.SYSTEM_OVERLAY_ID);

                nodeDesc = new VodDescriptor(clientAddr, utility, 0, BaseCommandLineConfig.DEFAULT_MTU);
                nodes = new ArrayList<VodDescriptor>();
                nodes.add(nodeDesc);

                PortBindRequest requestServer = new PortBindRequest(serverAddr.getPeerAddress(),
                        Transport.TCP);
                requestServer.setResponse(new PortBindResponse(requestServer) {
                });
                trigger(requestServer, server.getPositive(NatNetworkControl.class));
            }
        };
        public Handler<PortBindResponse> handlePortBindResponse = new Handler<PortBindResponse>() {
            @Override
            public void handle(PortBindResponse event) {
                logger.info("Port bind response");

                if (event.getStatus() == Status.FAIL) {
                    testObj.failAndRelease();
                    return;
                }

                if (event.getPort() == serverAddr.getPort()) {
                    trigger(new TConnectionMsg.Ping(clientAddr, serverAddr, Transport.TCP,
                            UUID.nextUUID()), client.getPositive(VodNetwork.class));
                }
            }
        };
        public Handler<TConnectionMsg.Ping> handlePing = new Handler<TConnectionMsg.Ping>() {
            @Override
            public void handle(TConnectionMsg.Ping event) {
                logger.info("Received ping");
                trigger(new TConnectionMsg.Pong(serverAddr, clientAddr, Transport.TCP, event.getTimeoutId()),
                        server.getPositive(VodNetwork.class));
            }
        };
        public Handler<TConnectionMsg.Pong> handlePong = new Handler<TConnectionMsg.Pong>() {
            @Override
            public void handle(TConnectionMsg.Pong event) {
                logger.info("Received pong");
                CloseConnectionRequest request = new CloseConnectionRequest(0, serverAddr.getPeerAddress(), Transport.TCP);
                request.setResponse(new CloseConnectionResponse(request));
                trigger(request, client.getPositive(NatNetworkControl.class));
            }
        };
        
        public Handler<CloseConnectionResponse> handleCloseConnectionResponse = new Handler<CloseConnectionResponse>() {
            @Override
            public void handle(CloseConnectionResponse event) {
                logger.info("Received CloseConnectionResponse");
                Set set = new HashSet<Integer>();
                set.add(serverAddr.getPort());
                PortDeleteRequest request = new PortDeleteRequest(0, set, Transport.UDT);
                request.setResponse(new PortDeleteResponse(request, 0) {
                });
                trigger(request, server.getPositive(NatNetworkControl.class));
            }
        };
        public Handler<PortDeleteResponse> handlePortDeletionResponse = new Handler<PortDeleteResponse>() {
            @Override
            public void handle(PortDeleteResponse event) {
                logger.info("Received PortDeleteResponse");
                trigger(new Stop(), client.getControl());
                trigger(new Stop(), server.getControl());
                testObj.pass();
            }
        };
        public Handler<MsgTimeout> handleMsgTimeout = new Handler<MsgTimeout>() {
            public void handle(MsgTimeout event) {
                logger.info("Msg timeout");
                trigger(new Stop(), client.getControl());
                trigger(new Stop(), server.getControl());
                testObj.testStatus = false;
                testObj.failAndRelease();
            }
        };
    }

    private static final int EVENT_COUNT = 1;
    private static Semaphore semaphore = new Semaphore(0);

    private void allTests() {
        runInstance();
        assertTrue(testStatus);
    }

    private void runInstance() {
        System.setProperty("java.net.preferIPv4Stack", "true");
        Kompics.createAndStart(TestStClientComponent.class, 1);

        try {
            TcpPingTest.semaphore.acquire(EVENT_COUNT);
            logger.info("Finished test.");
        } catch (InterruptedException e) {
            assert (false);
        } finally {
            Kompics.shutdown();
        }
    }

    @org.junit.Ignore
    public void testApp() {
        setTestObj(this);
        allTests();
    }

    public void pass() {
        TcpPingTest.semaphore.release();
    }

    public void failAndRelease() {
        testStatus = false;
        TcpPingTest.semaphore.release();
    }
}
