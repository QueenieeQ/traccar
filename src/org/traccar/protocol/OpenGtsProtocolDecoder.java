/*
 * Copyright 2013 - 2017 Anton Tananaev (anton@traccar.org)
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
package org.traccar.protocol;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.traccar.BaseHttpProtocolDecoder;
import org.traccar.DeviceSession;
import org.traccar.helper.DateBuilder;
import org.traccar.helper.Parser;
import org.traccar.helper.PatternBuilder;
import org.traccar.model.Position;

import java.net.SocketAddress;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class OpenGtsProtocolDecoder extends BaseHttpProtocolDecoder {

    private static final Pattern PATTERN = new PatternBuilder()
            .text("$GPRMC,")
            .number("(dd)(dd)(dd),")          // time (hhmmss)
            .expression("([AV]),")               // validity
            .number("(d+)(dd.d+),")              // latitude
            .expression("([NS]),")
            .number("(d+)(dd.d+),")              // longitude
            .expression("([EW]),")
            .number("(d+.d+),")                  // speed
            .number("(d+.d+),")                 // course
            .number("(dd)(dd)(dd),")             // date (ddmmyy)
            .any()
            .compile();

    public OpenGtsProtocolDecoder(OpenGtsProtocol protocol) {
        super(protocol);
    }

    @Override
    protected Object decode(
            Channel channel, SocketAddress remoteAddress, Object msg) throws Exception {

        HttpRequest request = (HttpRequest) msg;
        QueryStringDecoder decoder = new QueryStringDecoder(request.getUri());
        Map<String, List<String>> params = decoder.getParameters();

        Position position = new Position();
        position.setProtocol(getProtocolName());

        for (Map.Entry<String, List<String>> entry : params.entrySet()) {
            String value = entry.getValue().get(0);
            switch (entry.getKey()) {
                case "id":
                    DeviceSession deviceSession = getDeviceSession(channel, remoteAddress, value);
                    if (deviceSession == null) {
                        sendResponse(channel, HttpResponseStatus.BAD_REQUEST);
                        return null;
                    }
                    position.setDeviceId(deviceSession.getDeviceId());
                    break;
                case "gprmc":
                    Parser parser = new Parser(PATTERN, value);
                    if (!parser.matches()) {
                            sendResponse(channel, HttpResponseStatus.BAD_REQUEST);
                        return null;
                    }

                    DateBuilder dateBuilder = new DateBuilder()
                            .setTime(parser.nextInt(0), parser.nextInt(0), parser.nextInt(0));

                    position.setValid(parser.next().equals("A"));
                    position.setLatitude(parser.nextCoordinate());
                    position.setLongitude(parser.nextCoordinate());
                    position.setSpeed(parser.nextDouble(0));
                    position.setCourse(parser.nextDouble(0));

                    dateBuilder.setDateReverse(parser.nextInt(0), parser.nextInt(0), parser.nextInt(0));
                    position.setTime(dateBuilder.getDate());
                    break;
                case "alt":
                    position.setAltitude(Double.parseDouble(value));
                    break;
                case "batt":
                    position.set(Position.KEY_BATTERY_LEVEL, Double.parseDouble(value));
                    break;
                default:
                    break;
            }
        }

        if (position.getDeviceId() != 0) {
            sendResponse(channel, HttpResponseStatus.OK);
            return position;
        } else {
            sendResponse(channel, HttpResponseStatus.BAD_REQUEST);
            return null;
        }
    }

}
