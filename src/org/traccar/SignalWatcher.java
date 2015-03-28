package org.traccar;

import org.traccar.helper.Log;
import org.traccar.model.*;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Timestamp;
import java.util.*;

import java.text.SimpleDateFormat;
import java.util.Timer;

import java.net.URLEncoder;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

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
    private static long TERMINAL_EMPTY_SIGNAL_ALARM_INTERVAL = 2L * 60 * 1000;

    private Map<Long, Timer> accTimer;
    private Map<Long, Timer> positionTimer;
    private Map<Long, Timer> noiseTimer;
    private Map<Long, Timer> gsensorTimer;
    private Map<Long, Timer> orientSensorTimer;

    public SignalWatcher(DatabaseDataManager dbDataManager) throws Exception {
        this.dbDataManager = dbDataManager;
        //TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(null);

        this.accTimer = new HashMap<Long, Timer>();
        this.positionTimer = new HashMap<Long, Timer>();
        this.noiseTimer = new HashMap<Long, Timer>();
        this.gsensorTimer = new HashMap<Long, Timer>();
        this.orientSensorTimer = new HashMap<Long, Timer>();


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
        }, 15000);
    }

    public void setDataManager(DatabaseDataManager dbDataManager) {
        this.dbDataManager = dbDataManager;
    }

    public void Tick() throws Exception {
        try {
            for (final Device dev : dbDataManager.getDevices()) {
                if (dev.defence==1) {
                    final Signal signal = dbDataManager.selectLastSignal(dev.getId());
                    final Signal signalRaw = dbDataManager.selectLastSignalRaw(dev.getId());
                    final Position position = dbDataManager.selectLastPosition(dev.getId());

                    final Timestamp signalTime = new Timestamp(signal.getTime().getTime());
                    final Timestamp signalRawTime = new Timestamp(signalRaw.getTime().getTime());




                    final Timestamp positionTime = new Timestamp(position.getTime().getTime());

    //                if (signal.getGps()==0)
    //                   Alarm(7233, dev);

                    if (signal.getCharge()==0)
                        Alarm(7235, dev);


                    if (signal.getAcc()==1 && accTimer.get(dev.getId())==null) {
                        Log.info("DEV:"+dev.getId()+", accTimer START");
                        final Timer accDelay = new Timer();
                        accTimer.put(dev.getId(), accDelay);
                        accDelay.schedule(new TimerTask(){
                            @Override
                            public void run() {
                                Log.info("DEV:"+dev.getId()+", accTimer END");
                                try {
                                    Device checkDev = dbDataManager.getDeviceByID(dev.getId());
                                    Timestamp checkSignalTime = new Timestamp(dbDataManager.selectLastSignal(dev.getId()).getTime().getTime());
                                    if (checkDev.defence==1 && System.currentTimeMillis()-checkSignalTime.getTime()*1000 < 15*1000)  {
                                        Alarm(8624, checkDev);

                                        accDelay.cancel();
                                        accTimer.remove(dev.getId());
                                    }else if (checkDev.defence==0) {
                                        accDelay.cancel();
                                        accTimer.remove(dev.getId());
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                            }
                        }, 15*1000, 5*1000);
                    }


                    //Log.info("ALARM! System current time: "+System.currentTimeMillis()+", signal time: "+signalTime.getTime());

                    //position check
                    if (positionTimer.get(dev.getId())==null && dev.getDistance(position)>0.45) {
                        Log.info("DEV:"+dev.getId()+", positionTimer START");
                        final Timer defPosDelay = new Timer();
                        positionTimer.put(dev.getId(), defPosDelay);
                        defPosDelay.schedule(new TimerTask(){
                            @Override
                            public void run() {
                                Log.info("DEV:"+dev.getId()+", positionTimer END");
                                try {
                                    Device checkDev = dbDataManager.getDeviceByID(dev.getId());
                                    if (checkDev.defence==1 && checkDev.getDistance(position) > 0.45) {
                                        dbDataManager.setDefenceCoordsValue(checkDev.getId(), String.valueOf(position.getLatitude())+","+String.valueOf(position.getLongitude()));
                                        Alarm(9999, checkDev);
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                defPosDelay.cancel();
                                positionTimer.remove(dev.getId());
                            }
                        }, 5*1000);
                    }

                    //sensors
                    if (signal.getNoiseValue() > dev.setting_noise_volume_level && noiseTimer.get(dev.getId())==null) {
                        final Timer noiseDelay = new Timer();
                        noiseTimer.put(dev.getId(), noiseDelay);
                        noiseDelay.schedule(new TimerTask(){
                            @Override
                            public void run() {
                                try {
                                    Device checkDev = dbDataManager.getDeviceByID(dev.getId());
                                    Timestamp checkSignalTime = new Timestamp(dbDataManager.selectLastSignal(dev.getId()).getTime().getTime());
                                    if (checkDev.defence==1 && System.currentTimeMillis()-checkSignalTime.getTime()*1000 < 15*1000)  {
                                        Alarm(5551, dev);
                                        noiseDelay.cancel();
                                        noiseTimer.remove(dev.getId());
                                    }else if (checkDev.defence==0) {
                                        noiseDelay.cancel();
                                        noiseTimer.remove(dev.getId());
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }, 15*1000, 5*1000);
                    }
                    if (signal.getGSensor() > dev.setting_gsensor_level && gsensorTimer.get(dev.getId())==null) {
                        final Timer gsensorDelay = new Timer();
                        gsensorTimer.put(dev.getId(), gsensorDelay);
                        gsensorDelay.schedule(new TimerTask(){
                            @Override
                            public void run() {
                                try {
                                    Device checkDev = dbDataManager.getDeviceByID(dev.getId());
                                    Timestamp checkSignalTime = new Timestamp(dbDataManager.selectLastSignal(dev.getId()).getTime().getTime());
                                    if (checkDev.defence==1 && System.currentTimeMillis()-checkSignalTime.getTime()*1000 < 15*1000)  {
                                        Alarm(5552, dev);
                                        gsensorDelay.cancel();
                                        gsensorTimer.remove(dev.getId());
                                    }else if (checkDev.defence==0) {
                                        gsensorDelay.cancel();
                                        gsensorTimer.remove(dev.getId());
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }, 15*1000, 5*1000);
                    }
                    if (signal.getOrientSensorValue() > dev.setting_orientsensor_level && orientSensorTimer.get(dev.getId())==null) {
                        final Timer orientSensorDelay = new Timer();
                        orientSensorTimer.put(dev.getId(), orientSensorDelay);
                        orientSensorDelay.schedule(new TimerTask(){
                            @Override
                            public void run() {
                                try {
                                    Device checkDev = dbDataManager.getDeviceByID(dev.getId());
                                    Timestamp checkSignalTime = new Timestamp(dbDataManager.selectLastSignal(dev.getId()).getTime().getTime());
                                    if (checkDev.defence==1 && System.currentTimeMillis()-checkSignalTime.getTime()*1000 < 15*1000)  {
                                        Alarm(5553, dev);
                                        orientSensorDelay.cancel();
                                        orientSensorTimer.remove(dev.getId());
                                    }else if (checkDev.defence==0) {
                                        orientSensorDelay.cancel();
                                        orientSensorTimer.remove(dev.getId());
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                orientSensorDelay.cancel();
                                orientSensorTimer.remove(dev.getId());
                            }
                        }, 15*1000, 5*1000);
                    }

                    if ((dev.getId()==9) && System.currentTimeMillis() - signalRawTime.getTime()*1000 > SIGNAL_EMPTY_ALARM_INTERVAL) {
                        Alarm(8627, dev);
                    }else if (System.currentTimeMillis() - signalRawTime.getTime()*1000 > TERMINAL_EMPTY_SIGNAL_ALARM_INTERVAL){
                        Log.info("SignalTime TS: "+signalRawTime.getTime());
                        Log.info("Current TS: "+System.currentTimeMillis());
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
        dbDataManager.setCommandValue(dev.getId(), "set defence:0;");

        Signal signal = dbDataManager.selectLastSignal(dev.getId());
        Position position = dbDataManager.selectLastPosition(dev.getId());


        Date date = new Date();

        //String dateFormat = new SimpleDateFormat("dd/MM HH:mm:ss").format(new Date());
        SimpleDateFormat isoFormat = new SimpleDateFormat("dd/MM HH:mm:ss");
        TimeZone zone = TimeZone.getTimeZone("GMT+6");
        isoFormat.setTimeZone(zone);
        String dateFormat = isoFormat.format(new Date());

        Log.info("Onverify alarm. Type: "+alarmType+", device: "+dev.getId()+", phone: "+dev.getPhoneNumber()+", last signal: "+signal.getTime().getTime()+", now: "+System.currentTimeMillis());

//        StringBuilder url = new StringBuilder();
//        url.append("http://www.onverify.com/call.php?userid=5226&apipass=1837&template_id="+alarmType+"&number="+dev.getPhoneNumber());
//        SendCallAlarmProcess process = new SendCallAlarmProcess(url.toString());
//        try {
//            process.execute();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }

        String typeText;
        if (alarmType==7235)
            typeText = "CHARGE";
        else if (alarmType==8624)
            typeText = "ACC";
        else if (alarmType==8627)
            typeText = "GSM";
        else if (alarmType==7233)
            typeText = "GPS";
        else if (alarmType==9999)
            typeText = "POSITION";
        else if (alarmType==5551)
            typeText = "NOISE";
        else if (alarmType==5552)
            typeText = "GSENSOR";
        else if (alarmType==5553)
            typeText = "ORIENTATION";
        else
            typeText = String.valueOf(alarmType);




        StringBuilder urlsms = new StringBuilder();
        //urlsms.append("http://smspilot.ru/api.php?apikey=M16IZE9COK1G29J02S8ZXAJ7GSCA4B2Q43IC5BG80WMLW66QP4JE671L0324314D&to="+dev.getPhoneNumber()+"&send=Device"+dev.getId()+":"+typeText+"!");
        urlsms.append("http://smsc.ru/sys/send.php?login=scroll&psw=mabyvirus&sender=signalus&phones="+dev.getPhoneNumber()+"&mes="+URLEncoder.encode("Device"+dev.getId()+":"+typeText+"! "+dateFormat, "UTF-8"));
        SendCallAlarmProcess processsms = new SendCallAlarmProcess(urlsms.toString());
        try {
            processsms.execute();
        } catch (Exception e) {
            e.printStackTrace();
        }


        StringBuilder urlx = new StringBuilder();
        urlx.append("http://www.signalus.ru/outer/sendmail?subject=Device"+dev.getId()+"&msg=");
        urlx.append("alarm:"+typeText+";lastsig:"+signal.getTime().getTime()+";now:"+System.currentTimeMillis()+";lastposition:"+position.getLatitude()+","+position.getLongitude()+";defposition:"+dev.defenceCoords);
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
