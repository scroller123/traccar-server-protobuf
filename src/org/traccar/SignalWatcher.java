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

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


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

    ScheduledThreadPoolExecutor exec;

    private Map<Long, Float> gSensorValue;
    private Map<Long, Double> noiseValue;
    private Map<Long, Double> orientSensorValue;

    private int smsH=1;



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

        this.gSensorValue = new HashMap<Long, Float>();
        this.noiseValue = new HashMap<Long, Double>();
        this.orientSensorValue = new HashMap<Long, Double>();

        this.exec = new ScheduledThreadPoolExecutor(1);

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

        // start check income sms's
        exec.scheduleWithFixedDelay(new SmsCheckerTask(), 0, 10, TimeUnit.SECONDS);

    }

    public void setDataManager(DatabaseDataManager dbDataManager) {
        this.dbDataManager = dbDataManager;
    }

    class DeviceAccTimer extends TimerTask {
        private final long deviceId;
        private final Timer timer;

        DeviceAccTimer ( long deviceId, Timer timer ) {
            this.deviceId = deviceId;
            this.timer = timer;
        }

        public void run() {

            try {
                Device checkDev = dbDataManager.getDeviceByID(deviceId);
                Timestamp checkSignalTime = new Timestamp(dbDataManager.selectLastSignal(deviceId).getTime().getTime());

                Log.info("DEV:"+deviceId+", DeviceAccTimer END. defence:"+String.valueOf(checkDev.defence)+", defftime:"+String.valueOf(System.currentTimeMillis()-checkSignalTime.getTime()*1000));

                if (checkDev.defence==1 && System.currentTimeMillis()-checkSignalTime.getTime()*1000 < 15*1000)  {
                    Alarm(8624, checkDev);
                    timer.cancel();
                    accTimer.remove(deviceId);
                } else if(checkDev.defence==0) {
                    timer.cancel();
                    accTimer.remove(deviceId);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    class DevicePositionTimer extends TimerTask {
        private final long deviceId;
        private final Timer timer;

        DevicePositionTimer ( long deviceId, Timer timer ) {
            this.deviceId = deviceId;
            this.timer = timer;
        }

        public void run() {

            try {
                Device checkDev = dbDataManager.getDeviceByID(deviceId);
                Position checkPosition = dbDataManager.selectLastPosition(deviceId);
                Log.info("DEV:"+deviceId+", DevicePositionTimer END. defence:"+String.valueOf(checkDev.defence)+", distance:"+String.valueOf(checkDev.getDistance(checkPosition)));
                if (checkDev.defence==1 && checkDev.getDistance(checkPosition) > 0.45) {
                    dbDataManager.setDefenceCoordsValue(checkDev.getId(), String.valueOf(checkPosition.getLatitude()) + "," + String.valueOf(checkPosition.getLongitude()));
                    Alarm(8629, checkDev);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            timer.cancel();
            positionTimer.remove(deviceId);
        }
    }

    class DeviceNoiseTimer extends TimerTask {
        private final long deviceId;
        private final Timer timer;

        DeviceNoiseTimer ( long deviceId, Timer timer ) {
            this.deviceId = deviceId;
            this.timer = timer;
        }

        public void run() {

            try {
                Device checkDev = dbDataManager.getDeviceByID(deviceId);
                Timestamp checkSignalTime = new Timestamp(dbDataManager.selectLastSignal(deviceId).getTime().getTime());

                Log.info("DEV:"+deviceId+", DeviceNoiseTimer END. defence:"+String.valueOf(checkDev.defence)+", defftime:"+String.valueOf(System.currentTimeMillis()-checkSignalTime.getTime()*1000));

                if (checkDev.defence==1 && System.currentTimeMillis()-checkSignalTime.getTime()*1000 < 15*1000)  {
                    Alarm(5551, dbDataManager.getDeviceByID(deviceId));
                    timer.cancel();
                    noiseTimer.remove(deviceId);
                } else if(checkDev.defence==0) {
                    timer.cancel();
                    noiseTimer.remove(deviceId);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    class DeviceGSensorTimer extends TimerTask {
        private final long deviceId;
        private final Timer timer;

        DeviceGSensorTimer ( long deviceId, Timer timer ) {
            this.deviceId = deviceId;
            this.timer = timer;
        }

        public void run() {

            try {
                Device checkDev = dbDataManager.getDeviceByID(deviceId);
                Timestamp checkSignalTime = new Timestamp(dbDataManager.selectLastSignal(deviceId).getTime().getTime());
                Log.info("DEV:"+deviceId+", DeviceGSensorTimer END. defence:"+String.valueOf(checkDev.defence)+", defftime:"+String.valueOf(System.currentTimeMillis()-checkSignalTime.getTime()*1000));
                if (checkDev.defence==1 && System.currentTimeMillis()-checkSignalTime.getTime()*1000 < 15*1000)  {
                    Alarm(5552, dbDataManager.getDeviceByID(deviceId));
                    timer.cancel();
                    gsensorTimer.remove(deviceId);
                } else if(checkDev.defence==0) {
                    timer.cancel();
                    gsensorTimer.remove(deviceId);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    class DeviceOrientSensorTimer extends TimerTask {
        private final long deviceId;
        private final Timer timer;

        DeviceOrientSensorTimer ( long deviceId, Timer timer ) {
            this.deviceId = deviceId;
            this.timer = timer;
        }

        public void run() {

            try {
                Device checkDev = dbDataManager.getDeviceByID(deviceId);
                Timestamp checkSignalTime = new Timestamp(dbDataManager.selectLastSignal(deviceId).getTime().getTime());
                Log.info("DEV:"+deviceId+", DeviceOrientSensorTimer END. defence:"+String.valueOf(checkDev.defence)+", defftime:"+String.valueOf(System.currentTimeMillis()-checkSignalTime.getTime()*1000));
                if (checkDev.defence==1 && System.currentTimeMillis()-checkSignalTime.getTime()*1000 < 15*1000)  {
                    Alarm(5553, dbDataManager.getDeviceByID(deviceId));
                    timer.cancel();
                    orientSensorTimer.remove(deviceId);
                } else if(checkDev.defence==0) {
                    timer.cancel();
                    orientSensorTimer.remove(deviceId);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }


        }
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
    //                   Alarm(11589, dev);

                    if (signal.getCharge()==0)
                        Alarm(7235, dev);


                    if (signal.defence==1 && signal.getAcc()==1 && accTimer.get(dev.getId())==null) {
                        Log.info("DEV:"+dev.getId()+", accTimer START");
                        final Timer accDelay = new Timer();
                        accDelay.schedule(new DeviceAccTimer(dev.getId(), accDelay), 10*1000, 5*1000);
                        accTimer.put(dev.getId(), accDelay);
                    }

                    //Log.info("ALARM! System current time: "+System.currentTimeMillis()+", signal time: "+signalTime.getTime());

                    //position check
                    if (positionTimer.get(dev.getId())==null && dev.getDistance(position)>0.45) {
                        Log.info("DEV:"+dev.getId()+", positionTimer START");
                        final Timer defPosDelay = new Timer();
                        defPosDelay.schedule(new DevicePositionTimer(dev.getId(), defPosDelay), 5*1000);
                        positionTimer.put(dev.getId(), defPosDelay);
                    }

                    //sensors
                    if (signal.defence==1 && signal.getNoiseValue() > dev.setting_noise_volume_level && noiseTimer.get(dev.getId())==null) {
                        noiseValue.put(dev.getId(), signal.getNoiseValue());

                        Log.info("DEV:"+dev.getId()+", noiseTimer START");
                        final Timer noiseDelay = new Timer();
                        noiseDelay.schedule(new DeviceNoiseTimer(dev.getId(), noiseDelay), 15*1000, 5*1000);
                        noiseTimer.put(dev.getId(), noiseDelay);
                    }
                    if (signal.defence==1 && signal.getGSensor() > dev.setting_gsensor_level && gsensorTimer.get(dev.getId())==null) {
                        gSensorValue.put(dev.getId(), signal.getGSensor());

                        Log.info("DEV:"+dev.getId()+", gsensorTimer START");
                        final Timer gsensorDelay = new Timer();
                        gsensorDelay.schedule(new DeviceGSensorTimer(dev.getId(), gsensorDelay), 15*1000, 5*1000);
                        gsensorTimer.put(dev.getId(), gsensorDelay);
                    }
                    if (signal.defence==1 && signal.getOrientSensorValue() > dev.setting_orientsensor_level && orientSensorTimer.get(dev.getId())==null) {
                        orientSensorValue.put(dev.getId(), signal.getOrientSensorValue());

                        Log.info("DEV:"+dev.getId()+", orientTimer START");
                        final Timer orientSensorDelay = new Timer();
                        orientSensorDelay.schedule(new DeviceOrientSensorTimer(dev.getId(), orientSensorDelay), 15*1000, 5*1000);
                        orientSensorTimer.put(dev.getId(), orientSensorDelay);
                    }

                    if (System.currentTimeMillis() - signalRawTime.getTime()*1000 >= dev.alarmTimeInDefence*1000 &&
                        System.currentTimeMillis() - signalRawTime.getTime()*1000 <= dev.alarmTimeInDefence*1000+500){
                        Log.info("SignalTime TS: "+signalRawTime.getTime());
                        Log.info("Current TS: "+System.currentTimeMillis());
                        Alarm(8627, dev);
                    }

                    if (System.currentTimeMillis() - signalRawTime.getTime()*1000 >= 300*1000 &&
                            System.currentTimeMillis() - signalRawTime.getTime()*1000 <= 300*1000+1000){

                        StringBuilder urlx = new StringBuilder();
                        urlx.append("http://www.signalus.ru/outer/sendmail?subject=Device"+dev.getId()+"&msg=");
                        urlx.append("alarm:GSM;lastsig:"+signal.getTime().getTime()+";now:"+System.currentTimeMillis()+";lastposition:"+position.getLatitude()+","+position.getLongitude()+";defposition:"+dev.defenceCoords);
                        SendEmailProcess processx = new SendEmailProcess(urlx.toString());
                        try {
                            processx.execute();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
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



        if (alarmType!=8627) {
            dbDataManager.setDefenceValue(dev.getId(), 0);
            dbDataManager.setCommandValue(dev.getId(), "set defence:0;");
        }

        Signal signal = dbDataManager.selectLastSignal(dev.getId());
        Position position = dbDataManager.selectLastPosition(dev.getId());


        Date date = new Date();

        //String dateFormat = new SimpleDateFormat("dd/MM HH:mm:ss").format(new Date());
        SimpleDateFormat isoFormat = new SimpleDateFormat("dd/MM HH:mm:ss");
        TimeZone zone = TimeZone.getTimeZone("GMT+6");
        isoFormat.setTimeZone(zone);
        String dateFormat = isoFormat.format(new Date());

        Log.info("Onverify alarm. Type: "+alarmType+", device: "+dev.getId()+", phone: "+dev.getPhoneNumber()+", last signal: "+signal.getTime().getTime()+", now: "+System.currentTimeMillis());


        if (alarmType==7235 || alarmType==8624 || alarmType==11589 || alarmType==8629){
            StringBuilder url = new StringBuilder();
            url.append("http://www.onverify.com/call.php?userid=5226&apipass=1837&template_id="+alarmType+"&number="+dev.getPhoneNumber());
            SendCallAlarmProcess process = new SendCallAlarmProcess(url.toString());
            try {
                process.execute();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        String typeText;
        if (alarmType==7235)
            typeText = "CHARGE";
        else if (alarmType==8624)
            typeText = "ACC";
        else if (alarmType==8627)
            typeText = "GSM";
        else if (alarmType==11589)
            typeText = "GPS";
        else if (alarmType==8629)
            typeText = "POSITION";
        else if (alarmType==5551)
            typeText = "NOISE:"+(noiseValue.get(dev.getId()));
        else if (alarmType==5552)
            typeText = "GSENSOR:"+(gSensorValue.get(dev.getId()));
        else if (alarmType==5553)
            typeText = "ORIENTATION:"+(orientSensorValue.get(dev.getId()));
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




    class SmsCheckerTask implements Runnable {
        @Override
        public void run() {
            StringBuilder urlx = new StringBuilder();

//            Random rand = new Random();
//            int  n = rand.nextInt(10) + 1;
            urlx.append("http://smsc.ru/sys/get.php?get_answers=1&login=scroll&psw=mabyvirus&fmt=1&hour="+Integer.valueOf(smsH++));
            if (smsH>10) smsH = 1;

            SmsCheckerProcess p = new SmsCheckerProcess(urlx.toString());
            try {
                p.execute();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    public class SmsCheckerProcess extends SwingWorker {
        String url;

        public SmsCheckerProcess (String url){
            this.url = url;
        }
        /**
         * @throws Exception
         */
        protected Object doInBackground() throws Exception {
            final String USER_AGENT = "Mozilla/5.0";

            Log.info("Get SMS: "   +url);

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


                String line;
                while((line = in.readLine())!=null){
//                    Log.info("SMS:"+line);
                    String[] sms = line.split(",");

                    SimpleDateFormat isoFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
                    TimeZone zone = TimeZone.getTimeZone("GMT+3");
                    isoFormat.setTimeZone(zone);
                    Date date = isoFormat.parse(sms[1]);

                    if (System.currentTimeMillis()-date.getTime() <= 10L*1000) {
                        Log.info("id:"+sms[0]
                                +",received:"+sms[1]
                                +",phone:"+sms[2]
                                +",to_phone:"+sms[3]
                                +",sent:"+sms[4]
                                +",message:"+sms[5]);

                        String[] msg = ((String)sms[5]).split(":");



                        if (msg[2].equals("arm")) {
                            Device dev = dbDataManager.getDeviceByID(Long.valueOf(msg[3]));
                            for (String number : dev.setting_incoming_numbers.split(",")) {
                                if (("+"+(String)sms[2]).equals(number)) {
                                    Log.info(dev.getId().toString()+" ARM by SMS");
                                    dbDataManager.setDefenceValue(dev.getId(), 1);
                                    dbDataManager.setCommandValue(dev.getId(), "set defence:1;");

                                    Position lastPosition = dbDataManager.selectLastPosition(dev.getId());
                                    dbDataManager.setDefenceCoordsValue(dev.getId(), lastPosition.getLatitude()+","+lastPosition.getLongitude());

                                    break;
                                }
                            }
                        }else if (msg[2].equals("disarm")) {
                            Device dev = dbDataManager.getDeviceByID(Long.valueOf(msg[3]));
                            for (String number : dev.setting_incoming_numbers.split(",")) {
                                if (("+"+(String)sms[2]).equals(number)) {
                                    Log.info(dev.getId().toString()+" DISARM by SMS");
                                    dbDataManager.setDefenceValue(dev.getId(), 0);
                                    dbDataManager.setCommandValue(dev.getId(), "set defence:0;");
                                    break;
                                }
                            }
                        }
                    }



                }

                in.close();

            }catch (Exception e){
                Log.error("HTTP GET Exception: "+e.getMessage()+", "+e.getCause().getMessage());
            }

            return null;
        }
    }

}
