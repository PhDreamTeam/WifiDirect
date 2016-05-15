package pt.unl.fct.hyrax.wfmobilenetwork.wifidirect;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import pt.unl.fct.hyrax.wfmobilenetwork.wifidirect.utils.AndroidUtils;
import pt.unl.fct.hyrax.wfmobilenetwork.wifidirect.utils.Configurations;

import java.io.IOException;
import java.net.*;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;

/**
 * Created by DR & AT on 20/05/2015
 * .
 */
public class RelayActivity extends Activity {
    public static final String TAG = "Relay Activity";

    RelayActivity myThis;
    IStoppable crForwarder;
    Button btnStartStop, btnTcp;
    private Button btnAddNewRule;
    private EditText etCRNewRuleTo;
    private EditText etCRNewRuleUse;
    private TableLayout tableLayoutCRRules;

    private HashMap<String, String> relayRulesMap = new HashMap<>();
    private HashMap<String, Socket> controlConnectionsTo = new HashMap<>();
    private HashMap<String, Socket> controlConnectionsFrom = new HashMap<>();


    private NetworkInterface wfdNetworkInterface;
    private NetworkInterface wfNetworkInterface;
    private NetworkInterfacesDetector networkInterfacesDetector;
    private TextView tvWFDState;
    private TextView tvWFState;
    private Button btnCancelNewRule;
    private Button btnEditNewRule;
    private LinearLayout llAddNewRule;
    private Button btnClearRelayRule;
    private Button btnUdp;

    RELAY_TYPE relayType;
    private Button btnTCPOne4All;
    private Button btnConnectToControlSocket;
    private EditText etControlSocketIPaddress;
    private String CRPort;
    private EditText etCRPort;
    private Socket controlSocket;
    private EditText etControlSocketPortNumber;
    private Button btnReceiveFromControlSocket;
    private TextView tvControlConnectionsTo;
    private TextView tvControlConnectionsFrom;
    private ControlReceiverThread cReveiverThread;
    private Configurations configurations;

    enum RELAY_TYPE {TCP, UDP, TCP_ONE4ALL, TCP_ONE4ONE}

    ;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        myThis = this;
        super.onCreate(savedInstanceState);

        setContentView(R.layout.relay_activity);

        // read configurations from file
        configurations = Configurations.readFromConfigurationsFile();

        // get gui elements
        btnStartStop = (Button) findViewById(R.id.buttonStartStop);
        btnTcp = (Button) findViewById(R.id.btnRATCP);
        btnUdp = (Button) findViewById(R.id.btnRAUDP);
        btnTCPOne4All = (Button) findViewById(R.id.btnRATCPOne4All);

        etCRNewRuleTo = (EditText) findViewById(R.id.editTextCRNewRuleTo);
        etCRNewRuleUse = (EditText) findViewById(R.id.editTextCRNewRuleUse);
        etCRPort = (EditText) findViewById(R.id.editTextCrPortNumber);

        btnEditNewRule = (Button) findViewById(R.id.btnRAEditNewRule);
        btnClearRelayRule = (Button) findViewById(R.id.btnRAClearRelayRule);
        btnAddNewRule = (Button) findViewById(R.id.btnRAddNewCRRule);
        btnCancelNewRule = (Button) findViewById(R.id.btnRACancelAddCRRule);
        tableLayoutCRRules = (TableLayout) findViewById(R.id.tableLayoutRelayRules);
        llAddNewRule = (LinearLayout) findViewById(R.id.linerLayoutRAAddNewRule);

        relayType = btnTcp.getVisibility() == View.VISIBLE ? RELAY_TYPE.TCP :
                btnUdp.getVisibility() == View.VISIBLE ? RELAY_TYPE.UDP : RELAY_TYPE.TCP_ONE4ALL;

        btnConnectToControlSocket = (Button) findViewById(R.id.btnRAConnectControlSocket);
        btnReceiveFromControlSocket = (Button) findViewById(R.id.btnRAReceiveFromControlSocket);
        etControlSocketIPaddress = (EditText) findViewById(R.id.etRAControlSocketIPAddress);
        etControlSocketPortNumber = (EditText) findViewById(R.id.etRAControlSocketPortNumber);

        tvControlConnectionsTo = (TextView) findViewById(R.id.tvRAControlConnectionsTo);
        tvControlConnectionsFrom = (TextView) findViewById(R.id.tvRAControlConnectionsFrom);

        tvWFDState = (TextView) findViewById(R.id.tvRAWFDState);
        tvWFState = (TextView) findViewById(R.id.tvRAWFState);


        printNetworkInfo(getApplicationContext());

        // Listeners ==============================================

        btnStartStop.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (btnStartStop.getText().toString().equals("Start Relaying")) {
                            // start relaying
                            Context context = getApplicationContext();
                            CRPort = ((EditText) findViewById(R.id.editTextCrPortNumber)).getText().toString();
                            int bufferSize = 1024 * Integer.parseInt(
                                    ((EditText) findViewById(R.id.editTextCRMaxBufferSize)).getText().toString());

                            CharSequence text = "Start Relaying at localPort: " + CRPort + "!!!!!";
                            Toast toast = Toast.makeText(context, text, Toast.LENGTH_SHORT);
                            toast.show();

                            switch (relayType) {
                                case TCP:
                                    crForwarder = new CrForwardServerTCP(Integer.parseInt(CRPort)
                                            , ((TextView) findViewById(R.id.textViewTransferedDataOrigDest))
                                            , ((TextView) findViewById(R.id.textViewTransferedDataDestOrig))
                                            , bufferSize, relayRulesMap);
                                    break;
                                case UDP:
                                    crForwarder = new CrForwardServerUDP(Integer.parseInt(CRPort)
                                            , ((TextView) findViewById(R.id.textViewTransferedDataOrigDest))
                                            , bufferSize);
                                    break;
                                case TCP_ONE4ALL:

                            }
                            crForwarder.start();
                            btnStartStop.setText("Stop Relaying!!!");
                        } else {
                            // stop relaying
                            crForwarder.stopThread();
                            btnStartStop.setText("Start Relaying");
                        }
                    }
                });

        btnTcp.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        relayType = RELAY_TYPE.TCP;
                        btnTcp.setVisibility(View.GONE);
                        btnTCPOne4All.setVisibility(View.VISIBLE);
                    }
                });

        btnTCPOne4All.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        relayType = RELAY_TYPE.TCP_ONE4ALL;
                        btnUdp.setVisibility(View.VISIBLE);
                        btnTCPOne4All.setVisibility(View.GONE);
                    }
                });

        btnUdp.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        relayType = RELAY_TYPE.UDP;
                        btnTcp.setVisibility(View.VISIBLE);
                        btnUdp.setVisibility(View.GONE);
                    }
                });

        btnEditNewRule.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                llAddNewRule.setVisibility(View.VISIBLE);
                btnEditNewRule.setVisibility(View.GONE);
                btnClearRelayRule.setVisibility(View.GONE);
            }
        });

        btnAddNewRule.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addNewCRRule(etCRNewRuleTo.getText().toString(), etCRNewRuleUse.getText().toString());
                endOfNewRuleActions();
            }
        });

        btnCancelNewRule.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                endOfNewRuleActions();
            }
        });

        btnClearRelayRule.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (tableLayoutCRRules.getChildCount() == 1) {
                    Log.d(TAG, "Attempt to remove rule from an empty table, it was ignored.");
                    return;
                }

                // get last relay rule
                TableRow tr = (TableRow) tableLayoutCRRules.getChildAt(tableLayoutCRRules.getChildCount() - 1);
                String toAddress = ((TextView) tr.getChildAt(0)).getText().toString();

                // remove rule from GUI
                tableLayoutCRRules.removeView(tr);
                // remove rule from rules table
                relayRulesMap.remove(toAddress);
            }
        });

        btnConnectToControlSocket.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                makeControlSocket(etControlSocketIPaddress.getText().toString(),
                        Integer.parseInt(etControlSocketPortNumber.getText().toString()));
            }
        });

        btnReceiveFromControlSocket.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startReceivingFromControlSocket(
                        Integer.parseInt(etControlSocketPortNumber.getText().toString()));
            }
        });


        // network interfaces detector

        // WFD info listener
        WifiP2pManager.GroupInfoListener wfdGroupInfoListener = new WifiP2pManager.GroupInfoListener() {
            @Override
            public void onGroupInfoAvailable(WifiP2pGroup group) {
                String baseMsg = "WFD " + (configurations.isPriorityInterfaceWFD() ? "(PI)" : "") + ":  ";
                if (group == null) {
                    tvWFDState.setText(baseMsg + "OFF/NC");
                    wfdNetworkInterface = null;
                    //rbMulticastNetIntWFD.setVisibility(View.GONE);
                    return;
                }

                // group available: device connected

                // update global vars and GUI
                wfdNetworkInterface = AndroidUtils.getNetworkInterface(group.getInterface());
                //rbMulticastNetIntWFD.setVisibility(View.VISIBLE);

                // update GUI with info message
                tvWFDState.setText(baseMsg + "(" + (group.isGroupOwner() ? "GO" : group.getOwner().deviceName) +
                        ")  " + group.getInterface() + "  " +
                        WiFiDirectControlActivity.getLocalIpAddress(group.getInterface()));
            }
        };

        // WF info listener
        NetworkInterfacesDetector.WFNetworkInterfaceListener wfNetworkInterfaceListener = new NetworkInterfacesDetector.WFNetworkInterfaceListener() {
            public void updateWFNetworkInterface(NetworkInterface wfInterface) {
                wfNetworkInterface = wfInterface;
                String baseMsg = "WF " + (configurations.isPriorityInterfaceWF() ? "(PI)" : "") + ":  ";
                if (wfInterface == null) {
                    tvWFState.setText(baseMsg + "OFF/NC");
                    //rbMulticastNetIntWF.setVisibility(View.GONE);
                } else {
                    tvWFState.setText(baseMsg + AndroidUtils.getWifiSSID(RelayActivity.this) + "  " +
                            wfInterface.getName() + "  " +
                            AndroidUtils.getWifiLinkSpeed(RelayActivity.this) + "Mbps  " +
                            ClientActivity.getIPV4AddressWithBroadcast(wfInterface).toString().substring(1));
                    //rbMulticastNetIntWF.setVisibility(View.VISIBLE);
                }
            }
        };

        // create network interfaces detector
        networkInterfacesDetector = new NetworkInterfacesDetector(this, wfdGroupInfoListener,
                wfNetworkInterfaceListener);

        //        updateNetworkInterfaces();
        networkInterfacesDetector.updateNetworkInterfaces();

        // avoid keyboard popping up
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    /*
     *
     */
    private void makeControlSocket(final String ipAddress, final int portNumber) {
        Log.d(TAG, "Establishing a control socket with " + ipAddress + ":" + portNumber);

        Thread txThread = new Thread(new Runnable() {
            @Override
            public void run() {
                // establish a socket to ipAddress
                try {
                    Socket connSocketTo = new Socket(ipAddress, portNumber);
                    String socketIpAddress = connSocketTo.getRemoteSocketAddress().toString().substring(1);

                    // access to controlConnectionsTo must be thread safe
                    synchronized (controlConnectionsTo) {
                        controlConnectionsTo.put(socketIpAddress, connSocketTo);
                        final String msg = controlConnectionsTo.keySet().toString();

                        tvControlConnectionsTo.post(new Runnable() {
                            public void run() {
                                tvControlConnectionsTo.setText(msg);
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        txThread.start();
    }

    /*
     *
     */
    private void startReceivingFromControlSocket(int portNumber) {
        Log.d(TAG, "Start receiving from control socket at port " + portNumber);

        // start listening at the  port

        cReveiverThread = new ControlReceiverThread(portNumber);
        cReveiverThread.start();

        btnReceiveFromControlSocket.setEnabled(false);
    }

    /*
     *
     */
    private void addNewCRRule(String toAddress, String useCRAddress) {
        if (relayRulesMap.containsKey(toAddress)) {
            Log.d(TAG, "Attempt to duplicate toAddress (" + toAddress + ") rule, it was ignored.");
            return;
        }

        TableRow tr = new TableRow(this);

        TableRow.LayoutParams trp = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT
                , TableRow.LayoutParams.WRAP_CONTENT, 1.0f);

        TextView tvTo = new TextView(this);
        tvTo.setGravity(Gravity.CENTER);
        tvTo.setLayoutParams(trp);
        tvTo.setText(toAddress);
        tr.addView(tvTo);

        TextView tvUse = new TextView(this);
        tvUse.setGravity(Gravity.CENTER);
        tvUse.setLayoutParams(trp);
        tvUse.setText(useCRAddress);
        tr.addView(tvUse);

        tableLayoutCRRules.addView(tr);

        // Save rule
        relayRulesMap.put(toAddress, useCRAddress);
    }

    /*
     *
     */
    private void endOfNewRuleActions() {
        etCRNewRuleTo.setText("R");
        etCRNewRuleUse.setText("192.168.49.");
        llAddNewRule.setVisibility(View.GONE);
        btnEditNewRule.setVisibility(View.VISIBLE);
        btnClearRelayRule.setVisibility(View.VISIBLE);
    }

    /*
     *
     */
    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        if (crForwarder != null)
            crForwarder.stopThread();

        super.onDestroy();
    }

    /*
     *
     */
    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        networkInterfacesDetector.onResume();
    }

    /*
     *
     */
    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        //AndroidUtils.toast(this, "onPause");
        networkInterfacesDetector.onPause();

        if (cReveiverThread != null)
            cReveiverThread.stopThread();
    }


    /*
     *
     */
    class ControlReceiverThread extends Thread implements IStoppable {
        private ServerSocket controlSocket;
        private int controlPortNumber;

        public ControlReceiverThread(int controlPortNumber) {
            this.controlPortNumber = controlPortNumber;
        }

        @Override
        public void run() {
            try {
                controlSocket = new ServerSocket(controlPortNumber);
                while (true) {
                    Socket connSock = controlSocket.accept();
                    String socketIpAddress = connSock.getRemoteSocketAddress().toString().substring(1);
                    Log.d(TAG, "connSocket: " + connSock);

                    // HERE GET the interface from this socket

                    // access to controlConnectionsFrom must be thread safe
                    synchronized (controlConnectionsFrom) {
                        controlConnectionsFrom.put(socketIpAddress, connSock);
                        final String msg = controlConnectionsFrom.keySet().toString();
                        controlConnectionsFrom.notifyAll();
                        tvControlConnectionsFrom.post(new Runnable() {
                            @Override
                            public void run() {
                                tvControlConnectionsFrom.setText(msg);
                            }
                        });
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void stopThread() {
            try {
                controlSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /*
     *
     */
    private void printNetworkInfo(Context context) {
        // Debug networks
        String netStr = "";

//    TESTE 1
        ConnectivityManager connMng = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo nia[] = connMng.getAllNetworkInfo();
        for (NetworkInfo ni : nia) {
            netStr += "\t" + ni.getTypeName() + ", " + ni.getType() + "\n";
        }
        ((TextView) findViewById(R.id.textViewNetInfo)).append("getAllNetworkInfo: \n" + netStr);

//       Toast toast2 = Toast.makeText(context, netStr, Toast.LENGTH_SHORT);
//       toast2.show();

//    TESTE 2 - listar o nome das interfaces de rede
        Enumeration<NetworkInterface> nets = null;
        netStr = "";
        try {
            nets = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface netint : Collections.list(nets)) {
                netStr += "\t" + netint.getName() + ", " + netint.getDisplayName() + "\n";
                Enumeration<InetAddress> inetAdds = netint.getInetAddresses();
                for (InetAddress inetAddr : Collections.list((inetAdds))) {
                    netStr += "\t\t" + inetAddr + "\n";
                }
            }
            ((TextView) findViewById(R.id.textViewNetInfo)).append("\nGetNetworkInterfaces: \n" + netStr);

//            Toast toast3 = Toast.makeText(context, netStr, Toast.LENGTH_SHORT);
//            toast3.show();
        } catch (SocketException e) {
            e.printStackTrace();
        }

        //    TESTE 3 - listar o nome das interfaces de rede - API 21
//        Network[] nets2 = null;
//        netStr = "";
//        nets2 = connMng.getAllNetworks();
//        for (Network netint : nets2) {
//            netStr += "\t" + netint.toString() + "\n";
//        }
//        ((TextView) findViewById(R.id.textViewNetInfo)).append("\nGetAllNetworks: \n" + netStr);

//            Toast toast3 = Toast.makeText(context, netStr, Toast.LENGTH_SHORT);
//            toast3.show();

//    TESTE 4 - getActiveNetworkInfo - API 21
//        NetworkInfo netInfo = connMng.getActiveNetworkInfo();
//        ((TextView) findViewById(R.id.textViewNetInfo)).append("\nGetActiveNetworkInfo: \n" + netInfo.toString());
//
//        Network net = ConnectivityManager.getProcessDefaultNetwork();
//        ((TextView) findViewById(R.id.textViewNetInfo)).append(
//                "\nGetProcessDefaultNetwork: " + (net == null ? "null" : net.toString()));
//
//        //    TESTE 5 - getActiveNetworkInfo
//
//        // get WIFI Network
//        NetworkRequest netReq = new NetworkRequest.Builder().
//                addTransportType(NetworkCapabilities.TRANSPORT_WIFI).
//                build();
//
//        ConnectivityManager.NetworkCallback netCallBack = new ConnectivityManager.NetworkCallback() {
//            public void onAvailable(Network network) {
//                ((TextView) findViewById(R.id.textViewNetInfo)).append(
//                        "\nNetCallBack fired available: " + network);
//            }
//
//            public void onLost(Network network) {
//                ((TextView) findViewById(R.id.textViewNetInfo)).append(
//                        "\nNetCallBack lost fired: " + network);
//            }
//        };
//
//        connMng.requestNetwork(netReq, netCallBack);
//
//        // get P2P Network
//        // cada vez que se activa este callback d� um erro "o Wi-Fi Direct foi imterrompido"
//        // mesmo que comentado o interior do callback
//        // CONCLUS�O: N�O SE PODE UTILIZAR NO "OPO"
//        NetworkRequest netReq2 = new NetworkRequest.Builder().
//                addCapability(NetworkCapabilities.NET_CAPABILITY_WIFI_P2P).
//                //addTransportType(NetworkCapabilities.TRANSPORT_WIFI).
//                build();
//
//        ConnectivityManager.NetworkCallback netCallBack2 = new ConnectivityManager.NetworkCallback() {
//            public void onAvailable(Network network) {
//                ((TextView) findViewById(R.id.textViewNetInfo)).append(
//                        "\nNetCallBackP2P fired available: " + network);
//            }
//
//            public void onLost(Network network) {
//                ((TextView) findViewById(R.id.textViewNetInfo)).append(
//                        "\nNetCallBackP2P lost fired: " + network);
//            }
//        };
//        connMng.requestNetwork(netReq2, netCallBack2);

    }
}