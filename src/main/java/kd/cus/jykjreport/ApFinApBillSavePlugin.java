package kd.cus.jykjreport;

import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.utils.StringUtils;
import kd.bos.form.control.events.ItemClickEvent;
import kd.cus.common.ThrowableUtils;
import kd.fi.bcm.business.olap.OlapSaveBuilder;
import kd.fi.bcm.common.Pair;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Wu Yanqi
 */
public class ApFinApBillSavePlugin extends AbstractBillPlugIn {

    @Override
    public void itemClick(ItemClickEvent evt) {

        if (StringUtils.equals(evt.getItemKey(), "spic_baritemap")) {
            // 维度英文值：
            // Entity、Account、Scenario、Year、Period、Process、Currency、AuditTrail、ChangeType、MultiGAAP、C1、C2、C3、InternalCompany

            try {
                String modelNum = (String) this.getModel().getValue("spic_modenum");
                OlapSaveBuilder save = new OlapSaveBuilder(modelNum);
                save.setCrossDimensions(new String[]{
                        "Entity",
                        "Account",
                        "Year",
                        "Period",
                        "Scenario",
                        "C1",
                        "C2",
                        "C3"
                });
//设置固定维度成员--start
//        save.addFixedDimension("Entity", "Org-00017");
//        save.addFixedDimension("Account", "GDTY02101");
//        save.addFixedDimension("Year", "FY2020");
//        save.addFixedDimension("Period", "M_M01");
//        save.addFixedDimension("Scenario", "BRpt");
                save.addFixedDimension("Process", "IRpt");
                save.addFixedDimension("Currency", "EC");
                save.addFixedDimension("AuditTrail", "EntityInput");
                save.addFixedDimension("ChangeType", "CurrentPeriod");
                save.addFixedDimension("MultiGAAP", "PRCGAAP");
//        save.addFixedDimension("C1", "C1None");
//        save.addFixedDimension("C2", "C2None");
//        save.addFixedDimension("C3", "1GD");
                save.addFixedDimension("InternalCompany", "ICNone");
//----------end-------------
                save.setMeasures("FMONEY");
                List<Pair<String[], Object>> saveValPairs = new ArrayList<>();
                //多个值则for循环加
                String entity = (String) this.getModel().getValue("spic_entity");
                String year = (String) this.getModel().getValue("spic_year");
                String account = (String) this.getModel().getValue("spic_account");
                String period = (String) this.getModel().getValue("spic_period");
                String scenario = (String) this.getModel().getValue("spic_scenario");
                String c1 = (String) this.getModel().getValue("spic_c1");
                String c2 = (String) this.getModel().getValue("spic_c2");
                String c3 = (String) this.getModel().getValue("spic_c3");
                // spic_value
                BigDecimal value = (BigDecimal) this.getModel().getValue("spic_value");
                saveValPairs.add(Pair.onePair(new String[]{
                        entity,
                        year,
                        account,
                        period,
                        scenario,
                        c1,
                        c2,
                        c3}, value));
                save.setCellSet(saveValPairs);
                save.doSave();
            } catch (Exception e) {
                this.getModel().setValue("spic_errorinfo_tag", ThrowableUtils.getStackTrace(e));
            }
        }
    }

}
