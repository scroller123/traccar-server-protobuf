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
        device.setNeedRestart("0");
        return device;
    }
    @Override
    public void removeNeedRestart(Long deviceId) throws Exception {
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