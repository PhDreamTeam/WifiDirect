package pt.unl.fct.hyrax.wfmobilenetwork.wifidirect;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.TextView;
import pt.unl.fct.hyrax.wfmobilenetwork.wifidirect.utils.AndroidUtils;
import pt.unl.fct.hyrax.wfmobilenetwork.wifidirect.utils.LoggerSession;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;

/**
 * Created by DR & AT on 20/05/2015.
 * .
 */
public class ClientSendDataThreadTCP extends Thread implements IStoppable {
    public static final String LOG_TAG = ClientActivity.TAG + " TCP";

    private int bufferSize;
    String destIpAddress;
    int destPortNumber;
    String crIpAddress;
    int crPortNumber;
    long speed = 0; // number of millis to sleep between each 4096 of sent Bytes
    long dataLimit = 0;
    long rcvData = 0;
    double lastUpdate;
    Uri sourceUri;

    TextView tvSentData;
    TextView tvRcvData;
    ClientActivity clientActivity;

    Thread rcvThread;
    Socket cliSocket = null;

    /*
     *
     */
    public ClientSendDataThreadTCP(String destIpAddress, int destPortNumber, String crIpAddress, int crPortNumber
            , long speed, long dataLimitKB, TextView tvSentData, TextView tvRcvData, ClientActivity clientActivity, int bufferSize
            , Uri sourceUri) {
        this.destIpAddress = destIpAddress;
        this.destPortNumber = destPortNumber;
        this.crIpAddress = crIpAddress;
        this.crPortNumber = crPortNumber;
        this.speed = speed;
        this.dataLimit = dataLimitKB * 1024;
        this.tvSentData = tvSentData;
        this.tvRcvData = tvRcvData;
        this.clientActivity = clientActivity;
        this.bufferSize = bufferSize;
        this.sourceUri = sourceUri;
    }

    static NetworkInterface wifiInterface = null;

    /*
     *
     */
    static NetworkInterface getWLan0NetworkInterface() {
        if (wifiInterface == null) {
//            try {
//                Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
//
//                for (NetworkInterface networkInterface : Collections.list(networkInterfaces)) {
//                    if (networkInterface.getDisplayName().equals("wlan0")) {
//                        // || networkInterface.getDisplayName().equals("eth0")) {
//                        wifiInterface = networkInterface;
//                        break;
//                    }
//                }
//            } catch (SocketException e) {
//                e.printStackTrace();
//            }

            try {
                wifiInterface = NetworkInterface.getByName("wlan0");
            } catch (SocketException e) {
                e.printStackTrace();
            }
        }
        return wifiInterface;
    }

    /**
     * This method gets the InetAddress for this received address (trying to) using the wlan0 interface.
     * This is a test, to verify if we can send a message throught one speicify interface
     */
    private InetAddress getInetAddress(String destAddress) {
        try {
            // AT this code is working in ISEL - IPv6 experiments
            // InetAddress dest1 = Inet6Address.getByName(destAddress);
            // Inet6Address dest = Inet6Address.getByAddress(destAddress, dest1.getAddress(), getWLan0NetworkInterface());

            // AT this code worked in ISEL (with the ipv6 address)
            // InetAddress dest = Inet6Address.getByName(destAddress);

            // to work with ipv4 and ipv6
            return InetAddress.getByName(destAddress);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return null;
    }

    /*
     *
     */
    @Override
    public void run() {
        // Send data buffer, filled with numbers if not file to be transmitted
        byte buffer[] = new byte[bufferSize];
        if (sourceUri == null) {
            for (int i = 0; i < buffer.length; i++) {
                buffer[i] = (byte) i;
            }
        }

        ContentResolver cr = null;
        InputStream is = null;
        DataOutputStream dos = null;

        long fileSize = 0, sentData = 0;
        String fileName = null;

        LoggerSession logSession = null;

        try {
            cliSocket = new Socket(getInetAddress(crIpAddress), crPortNumber);
            dos = new DataOutputStream(cliSocket.getOutputStream());
            DataInputStream dis = new DataInputStream(cliSocket.getInputStream());

            String msg = "Start transmission to CR " + crIpAddress + ":" + crPortNumber + " with dest " +
                    destIpAddress + ":" + destPortNumber;
            AndroidUtils.toast(tvSentData, msg);
            Log.d(LOG_TAG, msg);

            // start log session and log initial time
            logSession = MainActivity.logger.getNewLoggerSession(this.getClass().getSimpleName(),
                    clientActivity.getLogDir());
            logSession.logMsg("Send data to CR: " + crIpAddress + ":" + crPortNumber);
            logSession.logMsg("Send data to dest: " + destIpAddress + ":" + destPortNumber + "\r\n");
            long initialTxTimeMs = logSession.logTime("Initial time");

            if (sourceUri != null) {
                cr = tvSentData.getContext().getContentResolver();
                is = cr.openInputStream(sourceUri);
                // get file size
                fileSize = cr.openTypedAssetFileDescriptor(sourceUri, "*/*", null).getLength();
                fileName = getFileNameFromURI(sourceUri);

                // Log.d(WiFiDirectActivity.TAG, "File URI: " + sourceUri.toString());
                Log.d(LOG_TAG, "Sending file: " + fileName + " with length (B): " + fileSize);
                dataLimit = 0; // send the complete image
            } else
                Log.d(LOG_TAG, "Sending data (B): " + dataLimit);


            // receive replies from destination
            rcvThread = createRcvThread(dis);

            // send destination information for the forward node
            String addressData = this.destIpAddress + ";" + this.destPortNumber;
            if (sourceUri != null) {
                addressData += ";" + fileName + ";" + fileSize;
            }
            dos.writeInt(addressData.getBytes().length);
            dos.write(addressData.getBytes());

            Log.d(LOG_TAG, "Using BufferSize (B): " + buffer.length);

            int dataLen = buffer.length;

            while (true) {
                if (is != null) {
                    dataLen = is.read(buffer);
                    if (dataLen == -1) {
                        break;
                    }
                }

                dos.write(buffer, 0, dataLen);
                sentData += dataLen;
                updateSentData(sentData, false);

                if (dataLimit != 0 && sentData >= dataLimit) {
                    break;
                }
                if (speed != 0) {
                    Thread.sleep(speed);
                }
            }

            updateSentData(sentData, true);
            Log.d(LOG_TAG, "EOT, data sent: " + sentData);

            cliSocket.shutdownOutput();

            // log end writing time
            long finalTxTimeMs = logSession.logTime("Final sent time");

            // wait for received thread to terminate and log time
            rcvThread.join();
            logSession.logTime("Final receive time");

            // log final sent and receive bytes
            logSession.logMsg("Data sent (B): " + sentData + ", (MB): " + sentData / (1024.0 * 1024));
            logSession.logMsg("Data received (B): " + rcvData + ", (MB): " + rcvData / (1024.0 * 1024));
            double sentDataMb = ((double) (sentData * 8)) / (1024 * 1024);
            double deltaTimeSegs = (finalTxTimeMs - initialTxTimeMs) / 1000.0;
            double dataSentSpeedMbps = sentDataMb / deltaTimeSegs;
            logSession.logMsg("Data sent speed (Mbps): " + String.format("%5.3f", dataSentSpeedMbps));
            logSession.close(tvSentData.getContext());

        } catch (Exception e) {
            String msg = "Transmission stopped, cause: " +
                    (e.getMessage().equals("Socket closed") ? "by user action" : e.getMessage());
            AndroidUtils.toast(tvSentData, msg);
            Log.e(LOG_TAG, msg);
            // e.printStackTrace();
            if (logSession != null) {
                logSession.logMsg(msg);
                logSession.close(tvSentData.getContext());
            }
        } finally {
            // close streams
            AndroidUtils.close(is);
            // close(dos);
        }

        tvSentData.post(new Runnable() {
            @Override
            public void run() {
                clientActivity.endTransmittingGuiActions(sourceUri);
            }
        });
    }


    /*
     *
     */
    private String getFileNameFromURI(Uri returnUri) {
        Cursor returnCursor =
                tvSentData.getContext().getContentResolver().query(returnUri, null, null, null, null);

        int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        //int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
        returnCursor.moveToFirst();

        String fileName = returnCursor.getString(nameIndex);
        //sizeView.setText(Long.toString(returnCursor.getLong(sizeIndex)));

        returnCursor.close();

        return fileName;
    }

    /*
     *
     */
    private Thread createRcvThread(final DataInputStream dis) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                byte buffer[] = new byte[bufferSize];

                int nBytesRead = 0;

                try {
                    while (true) {
                        nBytesRead = dis.read(buffer);
                        if (nBytesRead == -1)
                            break;

                        rcvData += nBytesRead;
                    }
                    updateRcvData();

                } catch (IOException e) {
                    String msg = "Socket receiver part stopped, cause: " +
                            (e.getMessage().equals("Socket closed") ? "by user action" : e.getMessage());
                    Log.d(LOG_TAG, msg);
                    // e.printStackTrace();
                } finally {
                    AndroidUtils.close(dis);
                }
            }
        });
        thread.start();
        return thread;
    }

    /*
     *
     */
    private void updateSentData(final long sentData, boolean forceUpdate) {
        long currentNanoTime = System.nanoTime();

        if ((currentNanoTime > lastUpdate + 1_000_000_000) || forceUpdate) {
            lastUpdate = currentNanoTime;
            tvSentData.post(new Runnable() {
                @Override
                public void run() {
                    tvSentData.setText("" + (sentData / 1024));
                }
            });
            updateRcvData();
        }
    }

    /*
     *
     */
    private void updateRcvData() {
        tvRcvData.post(new Runnable() {
            @Override
            public void run() {
                tvRcvData.setText("" + rcvData);
            }
        });
    }

    /*
     *
     */
    @Override
    public void stopThread() {
        //this.interrupt();
        //rcvThread.interrupt();

        try {
            if (cliSocket != null)
                cliSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}