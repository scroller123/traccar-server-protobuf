package org.traccar;

import org.traccar.helper.Log;
import org.traccar.model.DataManager;
import org.traccar.model.Device;
import org.traccar.model.Position;
import org.traccar.model.Signal;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Timestamp;
import java.util.List;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created with IntelliJ IDEA.
 * User: SCROLL
 * Date: 08.07.14
 * Time: 3:06
 * To change this template use File | Settings | File Templates.
 */
public class SignalWatcher {
    private ServerManager serverManager;
    private static long SIGNAL_EMPTY_ALARM_INTERVAL = 5L * 60 * 1000;

    public SignalWatcher(ServerManager serverManager) throws Exception {
        this.serverManager = serverManager;
        //TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        Tick();
    }

    public void Tick() throws Exception {
        for (Device dev : serverManager.getDataManager().getDevices()) {
            if (dev.defence==1) {
                Signal signal = serverManager.getDataManager().selectLastSignal(dev.getId());
                Position position = serverManager.getDataManager().selectLastPosition(dev.getId());

                Timestamp signalTime = new Timestamp(signal.getTime().getTime());
                Timestamp positionTime = new Timestamp(position.getTime().getTime());

//                if (signal.getGps()==0)
//                   Alarm(7233, dev);

                if (signal.getCharge()==0)
                    Alarm(7235, dev);

                if (signal.getAcc()==1)
                    Alarm(8624, dev);

                //Log.info("ALARM! System current time: "+System.currentTimeMillis()+", signal time: "+signalTime.getTime());
                if (System.currentTimeMillis() - signalTime.getTime()*1000 > SIGNAL_EMPTY_ALARM_INTERVAL)
                    Alarm(8627, dev);
            }
        }

        Timer timer = new Timer();
        timer.schedule(new TimerTask(){
            @Override
            public void run() {
                try {
                    Tick();
                } catch (Exception e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
            }
        }, 1000);

    }

    private void Alarm(int alarmType, Device dev) throws Exception {
        serverManager.getDataManager().setDefenceValue(dev.getId(), 0);

        Log.info("Onverify alarm. Type: "+alarmType+", device: "+dev.getId()+", phone: "+dev.getPhoneNumber());

        StringBuilder url = new StringBuilder();
        url.append("http://www.onverify.com/call.php?userid=5226&apipass=1837&template_id="+alarmType+"&number="+dev.getPhoneNumber());

        SendCallAlarmProcess process = new SendCallAlarmProcess(url.toString());
        try {
            process.execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public class SendCallAlarmProcess extends SwingWorker {
        String url;

        public SendCallAlarmProcess (String url){
            this.url = url;
        }
        /**
         * @throws Exception
         */
        protected Object doInBackground() throws Exception {
            final String USER_AGENT = "Mozilla/5.0";

            try{
                URL obj = new URL(url);
                HttpURLConnection con = (HttpURLConnection) obj.openConnection();
                con.setConnectTimeout(5000);
                con.setReadTimeout(5000);
                con.setRequestMethod("GET");
                con.setRequestProperty("User-Agent", USER_AGENT);
                int responseCode = con.getResponseCode();

                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();

                in.close();

                Log.info("Onverify event sended");

            }catch (Exception e){
                Log.error("Onverify HTTP GET Exception: " + e.getMessage() + ", " + e.getCause().getMessage());
            }

            return null;
        }
    }

}
