/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Controller;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ManagedProperty;
import javax.faces.bean.SessionScoped;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.servlet.http.HttpSession;
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
    private Map<String, Integer> map;
    private Map<String, Long> gap;
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
        if (sharedBean.getOneMap(sessionCode) == null) {
            sharedBean.getAllMaps().put(sessionCode, new HashMap());
        }
        sharedBean.getTime().put(sessionCode, new LocalDate());
        sharedBean.getGap().put(sessionCode, System.currentTimeMillis());
        if (sharedBean.getGroup().containsKey(sessionCode)) {
            sharedBean.getGroup().put(sessionCode, sharedBean.getGroup().get(sessionCode) + 1);
        } else {
            sharedBean.getGroup().put(sessionCode, 1);
        }

        //find and delete session codes older than 1 day in the shared bean.
        for (Entry<String, LocalDate> entry : sharedBean.getTime().entrySet()) {
            if (entry.getValue().isBefore(new LocalDate().minusDays(1))) {
                sharedBean.getAllMaps().remove(entry.getKey());
                sharedBean.getGap().remove(entry.getKey());
                sharedBean.getGroup().remove(entry.getKey());
            }
        }

        map = sharedBean.getOneMap(sessionCode);

        gap = sharedBean.getGap();
        for (Entry<String, Long> entry : gap.entrySet()) {
            if (map.containsKey(entry.getKey())) {
                if (System.currentTimeMillis() < entry.getValue() + 600000) {
                    groupSize++;
                } else {
                    sharedBean.getGrades().remove(entry.getKey());
                    sharedBean.getTime().remove(entry.getKey());
                    sharedBean.getGroup().put(entry.getKey(), 1);
                }
            }
        }

    }

    public Integer getGrade() {
        return grade;
    }

    public void setGrade(Integer grade) {
        this.grade = grade;
    }

    public Integer getGroupSize() {
        groupSize = 1;
        Map<String, Integer> groupMap = sharedBean.getGroup();
        if (groupMap != null) {
            groupSize = groupMap.get(sessionCode);
        }
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
        ExternalContext ec = FacesContext.getCurrentInstance().getExternalContext();
        HttpSession session = (HttpSession) ec.getSession(false);
        String sessionId = session.getId();

        if (grade != null && sessionId != null && map != null) {
            map = sharedBean.getOneMap(sessionCode);
            map.put(sessionId, grade * 50);
        } else {
            return;
        }

        Integer sumGrades = 0;
        Integer numberOfGrades = map.size();
        if (numberOfGrades == 0) {
            numberOfGrades = 1;
        }

        for (Entry<String, Integer> entry : map.entrySet()) {
            sumGrades = sumGrades + entry.getValue();
        }
        averageGrade = (float) sumGrades / numberOfGrades;

        meterGaugeChartModel = new MeterGaugeChartModel(averageGrade, intervals);
        RequestContext rc = RequestContext.getCurrentInstance();
        rc.update("gauge");

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
        map = sharedBean.getOneMap(sessionCode);
        if (map == null) {
            return;
        }
        int sumGrades = 0;
        int numberOfGrades = map.size();
        if (numberOfGrades == 0) {
            numberOfGrades = 1;
        }

        //don't update before 5 seconds have passed since last update
        Long last = sharedBean.getGap().get(sessionCode);
        if (System.currentTimeMillis() - last < 5000) {
            return;
        }
        sharedBean.getGap().put(sessionCode, System.currentTimeMillis());

        for (Entry<String, Integer> entry : map.entrySet()) {
            int value = entry.getValue();
            sumGrades = sumGrades + value;
            if (value > 51) {
                entry.setValue(value - 1);
            }
            if (value < 49) {
                entry.setValue(value + 1);
            }
        }
        
        if (map.isEmpty()) {
            sumGrades = 50;
        }
        averageGrade = (float) sumGrades / numberOfGrades;

        //don't update the chart if it is staying around 50
        if (averageGrade == null || (averageGrade > 49 & averageGrade < 51)) {
            return;
        }

        meterGaugeChartModel = new MeterGaugeChartModel(averageGrade, intervals);

    }

}
