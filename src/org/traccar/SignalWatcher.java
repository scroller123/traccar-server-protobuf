package org.traccar;

import org.traccar.helper.Log;
import org.traccar.model.*;

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
    private DatabaseDataManager dbDataManager;
    private static long SIGNAL_EMPTY_ALARM_INTERVAL = 5L * 60 * 1000;
    private static long TERMINAL_EMPTY_SIGNAL_ALARM_INTERVAL = 90 * 1000; // 90 sec

    public SignalWatcher(DatabaseDataManager dbDataManager) throws Exception {
        this.dbDataManager = dbDataManager;
        //TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        Tick();
    }

    public void setDataManager(DatabaseDataManager dbDataManager) {
        this.dbDataManager = dbDataManager;
    }

    public void Tick() throws Exception {
        try {
            for (Device dev : dbDataManager.getDevices()) {
                if (dev.defence==1) {
                    Signal signal = dbDataManager.selectLastSignal(dev.getId());
                    Position position = dbDataManager.selectLastPosition(dev.getId());

                    Timestamp signalTime = new Timestamp(signal.getTime().getTime());
                    Timestamp positionTime = new Timestamp(position.getTime().getTime());

    //                if (signal.getGps()==0)
    //                   Alarm(7233, dev);

                    if (signal.getCharge()==0)
                        Alarm(7235, dev);

                    if (signal.getAcc()==1)
                        Alarm(8624, dev);

                    //Log.info("ALARM! System current time: "+System.currentTimeMillis()+", signal time: "+signalTime.getTime());

                    if (dev.getId()==15 &&
                            System.currentTimeMillis() - signalTime.getTime()*1000 > TERMINAL_EMPTY_SIGNAL_ALARM_INTERVAL){

                        Log.info("SignalTime TS: "+signalTime.getTime());
                        Log.info("Current TS: "+System.currentTimeMillis());

                        Alarm(8627, dev);

    //                    StringBuilder url = new StringBuilder();
    //                    url.append("http://www.signalus.ru/outer/sendmail?subject=Device"+dev.getId()+"&msg=");
    //                    url.append("lost_signal_>"+TERMINAL_EMPTY_SIGNAL_ALARM_INTERVAL+",signalTS:"+signalTime.getTime()+",current:"+System.currentTimeMillis());
    //                    SendEmailProcess process = new SendEmailProcess(url.toString());
    //                    try {
    //                        process.execute();
    //                    } catch (Exception e) {
    //                        e.printStackTrace();
    //                    }
                    }else if (System.currentTimeMillis() - signalTime.getTime()*1000 > SIGNAL_EMPTY_ALARM_INTERVAL){
                        Alarm(8627, dev);
                    }
                }
            }
        } catch (Exception error) {
            Log.debug("Tick warn->>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
            Log.warning(error);
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
        }, 500);
    }

    private void Alarm(int alarmType, Device dev) throws Exception {

        Log.info("Device "+dev.getId()+" alarm "+alarmType);

        dbDataManager.setDefenceValue(dev.getId(), 0);

        Signal signal = dbDataManager.selectLastSignal(dev.getId());
        Position position = dbDataManager.selectLastPosition(dev.getId());

        Log.info("Onverify alarm. Type: "+alarmType+", device: "+dev.getId()+", phone: "+dev.getPhoneNumber()+", last signal: "+signal.getTime().getTime()+", now: "+System.currentTimeMillis());

        StringBuilder url = new StringBuilder();
        url.append("http://www.onverify.com/call.php?userid=5226&apipass=1837&template_id="+alarmType+"&number="+dev.getPhoneNumber());

        SendCallAlarmProcess process = new SendCallAlarmProcess(url.toString());
        try {
            process.execute();
        } catch (Exception e) {
            e.printStackTrace();
        }

        String typeText;
        if (alarmType==7235)
            typeText = "Charge";
        else if (alarmType==8624)
            typeText = "Acc";
        else if (alarmType==8627)
            typeText = "GSM";
        else if (alarmType==7233)
            typeText = "GPS";
        else
            typeText = String.valueOf(alarmType);




        StringBuilder urlsms = new StringBuilder();
        urlsms.append("http://smspilot.ru/api.php?apikey=M16IZE9COK1G29J02S8ZXAJ7GSCA4B2Q43IC5BG80WMLW66QP4JE671L0324314D&to="+dev.getPhoneNumber()+"&send=Device"+dev.getId()+",Car_alarm_type_"+typeText);
        SendCallAlarmProcess processsms = new SendCallAlarmProcess(urlsms.toString());
        try {
            processsms.execute();
        } catch (Exception e) {
            e.printStackTrace();
        }


        StringBuilder urlx = new StringBuilder();
        urlx.append("http://www.signalus.ru/outer/sendmail?subject=Device"+dev.getId()+"&msg=");
        urlx.append("alarm:"+typeText+",lastsig:"+signal.getTime().getTime()+",now:"+System.currentTimeMillis());
        SendEmailProcess processx = new SendEmailProcess(urlx.toString());
        try {
            processx.execute();
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

    public class SendEmailProcess extends SwingWorker {
        String url;

        public SendEmailProcess (String url){
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

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(con.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();

                in.close();

            }catch (Exception e){
                Log.error("HTTP GET Exception: "+e.getMessage()+", "+e.getCause().getMessage());
            }

            return null;
        }
    }

}
