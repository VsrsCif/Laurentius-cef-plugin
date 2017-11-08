  /*
 * To change this license header, choose License Headers in Project Properties. To change this
 * template file, choose Tools | Templates and open the template in the editor.
 */
package si.jrc.msh.plugin.cef.filter;

import java.util.Date;
import java.util.List;

/**
 *
 * @author Jože Rihtaršič
 */
public class StatusReportOutMailFilter {



  protected List<String> senderEBoxList;

  protected List<String> serviceList;

  protected List<String> statusList;

  protected Date statusDateFrom;
  protected Date statusDateTo;
  
  public List<String> getSenderEBoxList() {
    return senderEBoxList;
  }

  public void setSenderEBoxList(List<String> senderEBox) {
    this.senderEBoxList = senderEBox;
  }

  public List<String> getServiceList() {
    return serviceList;
  }

  public void setServiceList(List<String> serviceList) {
    this.serviceList = serviceList;
  }

  public List<String> getStatusList() {
    return statusList;
  }

  public void setStatusList(List<String> statusList) {
    this.statusList = statusList;
  }

  public Date getStatusDateFrom() {
    return statusDateFrom;
  }

  public void setStatusDateFrom(Date statusDateFrom) {
    this.statusDateFrom = statusDateFrom;
  }

  public Date getStatusDateTo() {
    return statusDateTo;
  }

  public void setStatusDateTo(Date statusDateTo) {
    this.statusDateTo = statusDateTo;
  }

 

 

}
