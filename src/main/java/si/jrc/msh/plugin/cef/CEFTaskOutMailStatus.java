/*
 * To change this license header, choose License Headers in Project Properties. To change this
 * template file, choose Tools | Templates and open the template in the editor.
 */
package si.jrc.msh.plugin.cef;

import java.io.StringWriter;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import si.jrc.msh.plugin.cef.filter.StatusReportOutMailFilter;
import si.laurentius.commons.SEDJNDI;
import si.laurentius.commons.SEDSystemProperties;
import si.laurentius.commons.SEDValues;
import si.laurentius.commons.enums.SEDOutboxMailStatus;
import si.laurentius.commons.exception.StorageException;
import si.laurentius.commons.interfaces.SEDDaoInterface;
import si.laurentius.commons.utils.SEDLogger;
import si.laurentius.commons.utils.Utils;
import si.laurentius.cron.SEDTaskExecution;
import si.laurentius.msh.outbox.mail.MSHOutMail;
import si.laurentius.msh.outbox.property.MSHOutProperties;
import si.laurentius.msh.outbox.property.MSHOutProperty;
import si.laurentius.plugin.crontask.CronTaskDef;
import si.laurentius.plugin.crontask.CronTaskPropertyDef;
import si.laurentius.plugin.interfaces.PropertyListType;
import si.laurentius.plugin.interfaces.PropertyType;
import si.laurentius.plugin.interfaces.TaskExecutionInterface;
import si.laurentius.plugin.interfaces.exception.TaskException;

/**
 *
 * @author Jože Rihtaršič
 */
@Stateless
@Local(TaskExecutionInterface.class)
public class CEFTaskOutMailStatus implements TaskExecutionInterface {

  protected static final SEDLogger LOG = new SEDLogger(
          CEFTaskOutMailStatus.class);
  public static final String KEY_REPORT_SEDBOXES = "report.status.sedboxes";

  @EJB(mappedName = SEDJNDI.JNDI_SEDDAO)
  SEDDaoInterface mdao;

  @Override
  public String executeTask(Properties p) throws TaskException {

    List<String> sedboxes = new ArrayList<>();

    if (!p.containsKey(KEY_REPORT_SEDBOXES)) {
      throw new TaskException(TaskException.TaskExceptionCode.InitException,
              "Missing parameter:  '" + KEY_REPORT_SEDBOXES + "'!");
    } else {
      String[] lstSB = p.getProperty(KEY_REPORT_SEDBOXES).split(",");
      for (String sb : lstSB) {
        sedboxes.add(sb + '@' + SEDSystemProperties.
                getLocalDomain());
      }
    }

    // respose for all values in last hour 
    Calendar c = Calendar.getInstance();
    c.set(Calendar.HOUR, -1);

    StatusReportOutMailFilter sf = new StatusReportOutMailFilter();
    sf.setSenderEBoxList(sedboxes);
    sf.setStatusDateFrom(c.getTime());
    sf.setStatusList(new ArrayList<>());
    sf.getStatusList().add(SEDOutboxMailStatus.FAILED.getValue());
    sf.getStatusList().add(SEDOutboxMailStatus.SENT.getValue());

    sf.setServiceList(new ArrayList<>());

    sf.getServiceList().add(CEFConstants.S_SERVICE_SIMPLE_ONEWAY);
    sf.getServiceList().add(CEFConstants.S_SERVICE_SIMPLE_TWOWAY);
    sf.getServiceList().add(CEFConstants.S_SERVICE_ONEWAY_RETRY);
    sf.getServiceList().add(CEFConstants.S_SERVICE_ONEWAY_SIGNONLY);

    // get outmails
    List<MSHOutMail> lst = mdao.getDataList(MSHOutMail.class, -1, 500, "Id",
            "ASC", sf);
    StringWriter sw = new StringWriter();
    sw.append("Got " + lst.size() + " mail to notifiy");
    for (MSHOutMail mo : lst) {
      if (Objects.equals(SEDOutboxMailStatus.FAILED.getValue(), mo.getStatus())) {
        fireAdviceOfDeliveryNotification(mo, CEFConstants.S_MINDER_NOTIFICTION_ERROR, SEDOutboxMailStatus.DELETED, "Mail not delivered");

      } else if (Objects.equals(SEDOutboxMailStatus.SENT.getValue(), mo.
              getStatus())) {
        fireAdviceOfDeliveryNotification(mo, CEFConstants.S_MINDER_NOTIFICTION_RECIEPT, SEDOutboxMailStatus.DELIVERED, "Mail delivered to minder");

      }
    }

    return sw.toString();

  }

  public void fireAdviceOfDeliveryNotification(MSHOutMail mOutMail, String type, SEDOutboxMailStatus sm, String statusDesc) {
    long l = LOG.logStart();

    // sender
    // receiver
    MSHOutMail mout = new MSHOutMail();
    mout.setMSHOutProperties(new MSHOutProperties());
    mout.setMessageId(Utils.getInstance().getGuidString());

    mout.setService(CEFConstants.S_SERVICE_CONF_TEST_ID);
    mout.setAction(CEFConstants.S_ACTION_CONF_TEST_NOTIFY);
    mout.setConversationId(mOutMail.getConversationId());
    mout.setRefToMessageId(mOutMail.getMessageId());
    mout.setSenderName(mOutMail.getSenderName());
    mout.setSenderEBox(mOutMail.getSenderEBox());
    mout.setReceiverName(CEFConstants.S_MINDER_NAME);
    mout.setReceiverEBox(CEFConstants.S_MINDER_ADDRESS);
    mout.getMSHOutProperties().getMSHOutProperties().add(createMSHOutProperty(
            CEFConstants.S_SERVICE_PROP, mOutMail.getService()));
    mout.getMSHOutProperties().getMSHOutProperties().add(createMSHOutProperty(
            CEFConstants.S_ACTION_PROP, mOutMail.getAction()));
    mout.getMSHOutProperties().getMSHOutProperties().add(createMSHOutProperty(
            CEFConstants.S_CONV_ID, mOutMail.getConversationId()));
    mout.getMSHOutProperties().getMSHOutProperties().add(createMSHOutProperty(
            CEFConstants.S_FROM_PARTY_ID_PROP, mOutMail.getSenderEBox().
                    substring(0,
                            mOutMail.getSenderEBox().indexOf("@"))));
    mout.getMSHOutProperties().getMSHOutProperties().add(createMSHOutProperty(
            CEFConstants.S_TO_PARTY_ID_PROP, mOutMail.getReceiverEBox().
                    substring(0,
                            mOutMail.getReceiverEBox().indexOf("@"))));
    mout.getMSHOutProperties().getMSHOutProperties().add(createMSHOutProperty(
            CEFConstants.S_REF_MESSAGE_ID, mOutMail.getMessageId()));
    mout.getMSHOutProperties().getMSHOutProperties().add(createMSHOutProperty(
            CEFConstants.S_SIGNAL_TYPE_ID, type));

    // create notification mail
    try {
      mdao.serializeOutMail(mout, "", "fireAdviceOfDeliveryNotification", "");
    } catch (StorageException ex) {
      LOG.logError(l, ex);
    }
    
    // set new status to out mail
    try {      
      mdao.setStatusToOutMail(mOutMail, sm,
              statusDesc);
    } catch (StorageException ex) {
      LOG.logError(l, ex);
    }

    LOG.logEnd(l);
  }
  
 

  private MSHOutProperty createMSHOutProperty(String prpName, String val) {
    MSHOutProperty mop = new MSHOutProperty();
    mop.setName(prpName);
    mop.setValue(val);
    return mop;
  }

  /**
   *
   * @return
   */
  @Override
  public CronTaskDef getDefinition() {
    CronTaskDef tt = new CronTaskDef();
    tt.setType("cef-outmail-status");
    tt.setName("cef outmail status");
    tt.setDescription("outgoing mail status notification");

    tt.getCronTaskPropertyDeves().add(createTTProperty(KEY_REPORT_SEDBOXES,
            "Local sedbox (without domain).", true, PropertyType.MultiList.
                    getType(), null, PropertyListType.LocalBoxes.getType(), null));

    return tt;
  }

  protected CronTaskPropertyDef createTTProperty(String key, String desc,
          boolean mandatory,
          String type, String valFormat, String valList, String defValue) {
    CronTaskPropertyDef ttp = new CronTaskPropertyDef();
    ttp.setKey(key);
    ttp.setDescription(desc);
    ttp.setMandatory(mandatory);
    ttp.setType(type);
    ttp.setValueFormat(valFormat);
    ttp.setValueList(valList);
    ttp.setDefValue(defValue);
    return ttp;
  }

}
