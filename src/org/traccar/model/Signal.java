/*
 * Copyright 2012 - 2013 Anton Tananaev (anton.tananaev@gmail.com)
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

import org.traccar.helper.Log;

import java.util.Date;
import java.sql.Timestamp;

/**
 * Signal information
 */
public class Signal  {


    private Date time;

    public Date getTime() {
        return time;
    }

    public void setTimeFromTimeStamp(long timestamp) {
        Timestamp timeStamp = new Timestamp(timestamp);
        this.time = new Date(timeStamp.getTime());
    }


    private int gps;

    public int getGps() {
        return gps;
    }

    public void setGps(int gps) {
        this.gps = gps;
    }


    private int charge;

    public int getCharge() {
        return charge;
    }

    public void setCharge(int charge) {
        this.charge = charge;
    }


    private int acc;

    public int getAcc() {
        return acc;
    }

    public void setAcc(int acc) {
        this.acc = acc;
    }


    private float gSensor;

    public float getGSensor() {
        return gSensor;
    }

    public void setGSensor(float gSensor) {
        this.gSensor = gSensor;
    }


    private double noiseValue;

    public double getNoiseValue() {
        return noiseValue;
    }

    public void setNoiseValue(double noiseValue) {
        this.noiseValue = noiseValue;
    }


}
