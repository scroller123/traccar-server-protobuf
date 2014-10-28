/*
 * Copyright 2012 Anton Tananaev (anton.tananaev@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.model;

/**
 * Device
 */
public class Device {



    /**
     * Id
     */
    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    /**
     * International mobile equipment identity (IMEI)
     */
    private String imei;

    public String getImei() {
        return imei;
    }

    public void setImei(String imei) {
        this.imei = imei;
    }

    /**
     * Phone number
     */
    private String phoneNumber;

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    /**
     * Unique id (for some trackers)
     */
    private String uniqueId;

    public String getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }


    /**
     * Do Update Version
     */
    private int doUpdateVersion;

    public int getDoUpdateVersion() {
        return doUpdateVersion;
    }

    public void setDoUpdateVersion(String value) {
        this.doUpdateVersion = Integer.valueOf(value);
    }


    /**
     * Do Searching Bluetooth
     */
    private int doSearchingBluetooth;

    public int getDoSearchingBluetooth() {
        return doSearchingBluetooth;
    }

    public void setDoSearchingBluetooth(String value) {
        this.doSearchingBluetooth = Integer.valueOf(value);
    }

    /**
     * Do Binding Bluetooth
     */
    private int doBindingBluetooth;

    public int getDoBindingBluetooth() {
        return doBindingBluetooth;
    }

    public void setDoBindingBluetooth(String value) {
        this.doBindingBluetooth = Integer.valueOf(value);
    }


    public double setting_noise_volume_level;
    public String setting_incoming_numbers;
    public float setting_gsensor_level;
    public float setting_orientsensor_level;
    public int setting_connect_timeout;
    public int setting_max_wait_time_to_change_sim;
    public int setting_max_fail_time_in_defence_to_change_sim;
    public int setting_max_wait_time_in_defence_to_change_sim;
    public int setting_max_response_wait_time;

    public int defence;

    public long signalTime;
    public int signalGps;
    public int signalCharge;
    public int signalAcc;
    public int signalGSensor;
    public int signalNoiseValue;

    public int positionLatitude;
    public int positionLongitude;
    public int positionTime;




    public int do_settings_update;


}
