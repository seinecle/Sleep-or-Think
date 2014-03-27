/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Controller;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ManagedProperty;
import javax.faces.bean.SessionScoped;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.joda.time.LocalDate;
import org.primefaces.context.RequestContext;
import org.primefaces.model.chart.MeterGaugeChartModel;

/*
 Copyright 2008-2013 Clement Levallois
 Authors : Clement Levallois <clementlevallois@gmail.com>
 Website : http://www.clementlevallois.net


 DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.

 Copyright 2013 Clement Levallois. All rights reserved.

 The contents of this file are subject to the terms of either the GNU
 General Public License Version 3 only ("GPL") or the Common
 Development and Distribution License("CDDL") (collectively, the
 "License"). You may not use this file except in compliance with the
 License. You can obtain a copy of the License at
 http://gephi.org/about/legal/license-notice/
 or /cddl-1.0.txt and /gpl-3.0.txt. See the License for the
 specific language governing permissions and limitations under the
 License.  When distributing the software, include this License Header
 Notice in each file and include the License files at
 /cddl-1.0.txt and /gpl-3.0.txt. If applicable, add the following below the
 License Header, with the fields enclosed by brackets [] replaced by
 your own identifying information:
 "Portions Copyrighted [year] [name of copyright owner]"

 If you wish your version of this file to be governed by only the CDDL
 or only the GPL Version 3, indicate your decision by adding
 "[Contributor] elects to include this software in this distribution
 under the [CDDL or GPL Version 3] license." If you do not indicate a
 single choice of license, a recipient has the option to distribute
 your version of this file under either the CDDL, the GPL Version 3 or
 to extend the choice of license to its licensees as provided above.
 However, if you add GPL Version 3 code and therefore, elected the GPL
 Version 3 license, then the option applies only if the new code is
 made subject to such option by the copyright holder.

 Contributor(s): Clement Levallois

 */
@ManagedBean
@SessionScoped
public class Bean implements Serializable {

    @ManagedProperty(value = "#{sharedBean}")
    SharedBean sharedBean;
    private String sessionCode;
    private Integer grade;
    private MeterGaugeChartModel meterGaugeChartModel;
    private List<Number> intervals;
    private Map<String, Integer> mapIPToGrade;
    private Map<String, Long> mapIPToTime;
    private Float averageGrade;
    private Integer groupSize;

    public Bean() {
        createMeterGaugeModel();
    }

    public void setSharedBean(SharedBean sharedBean) {
        this.sharedBean = sharedBean;
    }

    public String getSessionCode() {
        return sessionCode;
    }

    public void setSessionCode(String sessionCode) {
        this.sessionCode = sessionCode;
        ExternalContext ec = FacesContext.getCurrentInstance().getExternalContext();

        //setting a cookie to remember user on this session IF there is not already a cookie set. Cookies expire after 4 hours or so.
        Map<String, Object> cookies = ec.getRequestCookieMap();
        Cookie cookie = (Cookie) cookies.get(sessionCode);
        if (cookie == null) {
            HttpServletResponse response = (HttpServletResponse) ec.getResponse();
            String uuid = UUID.randomUUID().toString();
            cookie = new Cookie(sessionCode, uuid);
            cookie.setMaxAge(15000); // Expire time. -1 = by end of current session, 0 = immediately expire it, otherwise just the lifetime in seconds.
            response.addCookie(cookie);
        }

        //if this is a new session code, create an entry in the two shared maps
        if (!sharedBean.getMapSessionCodesToDay().containsKey(sessionCode)) {
            sharedBean.getMapSessionCodesToDay().put(sessionCode, new LocalDate());
            sharedBean.getMapSessionCodesToIPsToGrades().put(sessionCode, new HashMap());
            sharedBean.getMapSessionCodesToIPsToTime().put(sessionCode, new HashMap());
        }

        //find and delete votes that were cast more than 5 minutes ago
        //find and delete session codes older than 1 day in the shared bean.
        findAndDeleteOldVotes(5);
        findAndDeleteOldSessionCodes();

        setTheGroupSize();

    }

    public Integer getGrade() {
        return grade;
    }

    public void setGrade(Integer grade) {
        this.grade = grade;
    }

    public Integer getGroupSize() {
        return groupSize;
    }

    public void setGroupSize(Integer groupSize) {
        this.groupSize = groupSize;
    }

    public MeterGaugeChartModel getMeterGaugeChartModel() {
        return meterGaugeChartModel;
    }

    public void setMeterGaugeChartModel(MeterGaugeChartModel meterGaugeChartModel) {
        this.meterGaugeChartModel = meterGaugeChartModel;
    }

    public void confirmGrade() {
        try {
            Map<String, Object> cookies = FacesContext.getCurrentInstance().getExternalContext().getRequestCookieMap();
            Cookie cookie = (Cookie) cookies.get(sessionCode);
            String ipAddress = cookie.getValue();

            if (grade != null && ipAddress != null) {
                mapIPToGrade = sharedBean.getOneMapIPToGrade(sessionCode);
                mapIPToGrade.put(ipAddress, grade * 50);
                mapIPToTime = sharedBean.getOneMapIPToTime(sessionCode);
                mapIPToTime.put(ipAddress, System.currentTimeMillis());
            } else {
                return;
            }

            Integer sumGrades = 0;
            Integer numberOfGrades = mapIPToGrade.size();
            if (numberOfGrades == 0) {
                numberOfGrades = 1;
            }

            for (Entry<String, Integer> entry : mapIPToGrade.entrySet()) {
                sumGrades = sumGrades + entry.getValue();
            }
            averageGrade = (float) sumGrades / numberOfGrades;

            meterGaugeChartModel = new MeterGaugeChartModel(averageGrade, intervals);
            RequestContext rc = RequestContext.getCurrentInstance();
            rc.update("gauge");
        } catch (NullPointerException e) {
            System.out.println("NPE in confirmGrade(): " + e.getMessage());
        }

    }

    private void createMeterGaugeModel() {

        intervals = new ArrayList<Number>() {
            {
                add(20);
                add(40);
                add(60);
                add(80);
                add(100);
            }
        };

        meterGaugeChartModel = new MeterGaugeChartModel(50, intervals);
    }

    public void erosion() {
        try {

            //find and delete old votes
            findAndDeleteOldVotes(5);
            setTheGroupSize();

            //don't update before 3 seconds have passed since last update
            Long last = 0l;
            mapIPToTime = sharedBean.getOneMapIPToTime(sessionCode);
            for (Long time : mapIPToTime.values()) {
                if (time > last) {
                    last = time;
                }
            }

            if (System.currentTimeMillis() - last < 5000) {
                return;
            }

            mapIPToGrade = sharedBean.getOneMapIPToGrade(sessionCode);
            if (mapIPToGrade == null) {
                return;
            }
            int sumGrades = 0;
            int numberOfGrades = mapIPToGrade.size();
            if (numberOfGrades == 0) {
                numberOfGrades = 1;
            }

            for (Entry<String, Integer> entry : mapIPToGrade.entrySet()) {
                int value = entry.getValue();
                sumGrades = sumGrades + value;

                if (value > 51) {
                    entry.setValue(value - 1);
                }
                if (value < 49) {
                    entry.setValue(value + 1);
                }
            }

            if (mapIPToGrade.isEmpty()) {
                sumGrades = 50;
            }
            averageGrade = (float) sumGrades / numberOfGrades;

            //don't update the chart if it is staying around 50
            if (averageGrade == null || (averageGrade > 49 & averageGrade < 51)) {
                return;
            }

            meterGaugeChartModel = new MeterGaugeChartModel(averageGrade, intervals);
        } catch (NullPointerException e) {
            System.out.println("NPE in erosion(): " + e.getMessage());
        }

    }

    private void findAndDeleteOldSessionCodes() {
        try {
            Iterator<Entry<String, LocalDate>> iterator = sharedBean.getMapSessionCodesToDay().entrySet().iterator();
            Entry<String, LocalDate> entry;
            while (iterator.hasNext()) {
                entry = iterator.next();
                if (entry.getValue().isBefore(new LocalDate().minusDays(1))) {
                    sharedBean.getMapSessionCodesToIPsToGrades().remove(entry.getKey());
                    sharedBean.getMapSessionCodesToIPsToTime().remove(entry.getKey());
                    iterator.remove();
                }
            }
        } catch (NullPointerException e) {
            System.out.println("NPE in findAndDeleteOldSessionCodes(): " + e.getMessage());
        }

    }

    private void findAndDeleteOldVotes(Integer minutes) {
        try {
            Iterator<Entry<String, Long>> iterator = sharedBean.getOneMapIPToTime(sessionCode).entrySet().iterator();
            Entry<String, Long> entry;
            Long now = System.currentTimeMillis();

            //deletes IPS and their votes if older than n minutes
            while (iterator.hasNext()) {
                entry = iterator.next();
                if (now - (minutes * 60000) > entry.getValue()) {
//                if (now - 10000 > entry.getValue()) {
                    //we do not delete the vote of the last person
                    if (sharedBean.getOneMapIPToGrade(sessionCode).size() > 1) {
                        sharedBean.getOneMapIPToGrade(sessionCode).remove(entry.getKey());
                        iterator.remove();
                    }
                }

            }
        } catch (NullPointerException e) {
            System.out.println("NPE in findAndDeleteOldVotes(): " + e.getMessage());
        }

    }

    private void setTheGroupSize() {
        //set the group size (+1 if the first user has not added a grade yet)
        if (sharedBean.getMapSessionCodesToIPsToGrades().get(sessionCode).isEmpty()) {
            groupSize = 1;
        } else {
            groupSize = sharedBean.getMapSessionCodesToIPsToGrades().get(sessionCode).size();
        }
    }
}
