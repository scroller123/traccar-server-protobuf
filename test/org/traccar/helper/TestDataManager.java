package org.traccar.helper;

import java.util.ArrayList;
import java.util.List;

import org.traccar.model.*;

public class TestDataManager implements DataManager {

    @Override
    public List getDevices() {
        return null;
    }
    @Override
    public Device getDeviceByImei(String imei) {
        Device device = new Device();
        device.setId(new Long(1));
        device.setImei("123456789012345");
        device.setDoSearchingBluetooth("1");
        return device;
    }



    @Override
    public void refreshDevices(){
    }

    @Override
    public Device getDeviceByID(Long id){
        return null;
    }

    @Override
    public void setTimeZone(){}

    @Override
    public void deleteBluetoothSearchResult(Long deviceId){}
    @Override
    public void insertBluetoothSearchResult(Long deviceId, String name, String mac) {}

    @Override
    public ArrayList<BluetoothDevice> selectBluetoothBinded(Long deviceId) {
        return null;
    }

    @Override
    public Signal selectLastSignal(Long deviceId){
        return null;
    }

    @Override
    public Position selectLastPosition(Long deviceId){
        return null;
    }


    @Override
    public void setDefenceValue(Long deviceId, int value)  {}

    @Override
    public void setVersionValue(Long deviceId, int value)  {}

    @Override
    public void setDoUpdateVersionValue(Long deviceId, int value) {}

    @Override
    public void setDoSearchingBluetoothValue(Long deviceId, int value)  {}

    @Override
    public void setDoBindingBluetoothValue(Long deviceId, int value)  {}

    @Override
    public void setDoSettingsUpdateValue(Long deviceId, int value)  {}

    @Override
    public Long addPosition(Position position) {
        return null;
    }
    @Override
    public void updateLatestPosition(Long deviceId, Long positionId) throws Exception {
    }

    @Override
    public Long addSig(String hex,
                       int active_sim,
                       String version,
                       int defence,
                       String adds,
                       String gps,
                       int satellites,
                       int satellites_all,
                       String charge,
                       String acc,
                       String voltage,
                       double g_sensor,
                       double orient_sensor,
                       double noise_value,
                       String cell1,
                       String cell2,
                       String signal) {
        return null;
    }
}
