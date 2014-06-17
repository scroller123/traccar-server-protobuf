package org.traccar.helper;

import java.util.List;
import org.traccar.model.DataManager;
import org.traccar.model.Device;
import org.traccar.model.Position;

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
    public void deleteBluetoothSearchResult(Long deviceId){}
    @Override
    public void insertBluetoothSearchResult(Long deviceId, String name, String mac) {}

    @Override
    public void setDoSearchingBluetootValue(Long deviceId, int value)  {
    }

    @Override
    public Long addPosition(Position position) {
        return null;
    }
    @Override
    public void updateLatestPosition(Long deviceId, Long positionId) throws Exception {
    }

    @Override
    public Long addSig(String s1, String s2,  String gps, String charge, String acc, String voltage, String signal) {
        return null;
    }
}
