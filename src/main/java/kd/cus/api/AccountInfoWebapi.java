package kd.cus.api;

import io.netty.util.internal.ThrowableUtil;
import kd.bos.bill.AbstractBillWebApiPlugin;
import kd.bos.bill.IBillWebApiPlugin;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.metadata.dynamicobject.DynamicObjectType;
import kd.bos.dataentity.utils.StringUtils;
import kd.bos.entity.api.ApiResult;
import kd.bos.entity.operate.result.IOperateInfo;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.service.business.datamodel.DynamicFormModelProxy;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.MetadataServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.fi.iep.util.BussinessAndOperProvider;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 账户信息接口（司库）
 */
public class AccountInfoWebapi implements IBillWebApiPlugin {
    private static final String BANK_ACCOUNT_INFO_LOGO = "am_accountbank";//银行账号信息
    private static final String ORG_LOGO = "bos_org";//组织
    private static final String CURRENCY_LOGO = "bd_currency";//币别
    private static final String ACCTPURPOSE_LOGO = "bd_acctpurpose";//账户用途
    private static final String FINORGINFO_LOGO = "bd_finorginfo";//
    private static final String SETTLEMENTTYPE_LOGO = "bd_settlementtype";
    private static final String USER_LOGO = "bos_user";//用户
    private static final String STRATEGY_LOGO = "am_strategy";
    private static final String NETBANKACCT_LOGO = "bd_netbankacct";

    /**
     * @param params
     * @return
     */
    @Override
    public ApiResult doCustomService(Map<String, Object> params) {
        // 前台打印日志（往单据中写）
        LogEntity logResult = LogBillUtils.createLog(params.toString(), "", "司库", "", "accountInfo");
        // 后台打印日志
        Date startDate = LogUtils.log(null, "accountInfo", "开始传输", "", "", null, null);
        ApiResult apiResult = new ApiResult();
        try {
            Object operate_type = params.get("operate_type"),//操作
//                number = params.get("number"),//编码
                    bankaccountnumber = params.get("bankaccountnumber"),//银行账号
                    name = params.get("name"),//银行账户名称
                    englishname = params.get("englishname"),//银行账户名称（英文）
                    company = params.get("company"),//申请公司--基础资料
                    openorg = params.get("openorg"),//开户公司--基础资料
                    finorgtype = params.get("finorgtype"),//金融机构类别
                    acctproperty = params.get("acctproperty"),//账户用途--基础资料
                    bank = params.get("bank"),//开户行--基础资料
                    acctstyle = params.get("acctstyle"),//账户类型
                    accttype = params.get("accttype"),//账户性质
                    acctstatus = params.get("acctstatus"),//账户状态
                    closedate = params.get("closedate"),//销户日期
                    closereason = params.get("closereason"),//销户原因
                    opendate = params.get("opendate"),//开户日期
                    settlementtype = params.get("settlementtype"),//限定结算方式--基础资料
                    defaultcurrency = params.get("defaultcurrency"),//默认币别--基础资料
                    comment = params.get("comment"),//备注
                    shortnumber = params.get("shortnumber"),//助记码
//                manager = params.get("manager"),//账户管理人--基础资料
//                managecurrency = params.get("managecurrency"),//账户管理费--基础资料
//                acctmanageamt = params.get("acctmanageamt"),//账户管理费
//                isdefaultrec = params.get("isdefaultrec"),//默认收款户
//                isdefaultpay = params.get("isdefaultpay"),//默认付款户
//                strategy = params.get("strategy"),//账户管理策略--基础资料
                    issetbankinterface = params.get("issetbankinterface"),//开通银企接口
                    bebankfunc = params.get("bebankfunc"),//银企功能
                    isopenbank = params.get("isopenbank"),//开通网上银行
                    bankfunc = params.get("bankfunc"),//网上银行功能
                    acctname = params.get("acctname"),//银企账户名称
                    bankinterface = params.get("bankinterface"),//银企接口
                    netbank = params.get("netbank");//网银子账户--基础资料

            List<Map<String, Object>> currencylist = (List<Map<String, Object>>) params.get("currencylist");//币别--基础资料
            DynamicObject this_dy = null;
            if ("add".equals(operate_type)) {
                Map<Class<?>, Object> services = new HashMap<>();
                DynamicFormModelProxy model = new DynamicFormModelProxy(BANK_ACCOUNT_INFO_LOGO, UUID.randomUUID().toString(), services);
                model.createNewData();
                this_dy = model.getDataEntity();
            } else if ("update".equals(operate_type)) {
                //检验数据是否存在
                QFilter qFilter[] = {new QFilter("bankaccountnumber", QCP.equals, bankaccountnumber)};
//                String properites = StringUtils.join(MetadataServiceHelper.getDataEntityType(BANK_ACCOUNT_INFO_LOGO)
//                        .getAllFields().entrySet().stream().map(Map.Entry::getKey).toArray(), ",");
//            DynamicObject this_dy = BusinessDataServiceHelper.loadSingle(BANK_ACCOUNT_INFO_LOGO,
//                    "ctrlstrategy,useorg,org,createorg,strategy,manager,managecurrency,acctmanageamt,isdefaultrec,isdefaultpay,bankaccountnumber,name,englishname,company,openorg,finorgtype,acctproperty,bank,acctstyle,accttype,acctstatus,closedate,closereason,opendate,settlementtype,defaultcurrency,comment,shortnumber,issetbankinterface,bebankfunc,isopenbank,bankfunc,acctname,bankinterface,netbank,currency,spic_currencylist,spic_currencylist.spic_entry_currency,spic_currencylist.spic_currency_status,spic_currencylist.spic_account_name", qFilter);
                this_dy = BusinessDataServiceHelper.loadSingleFromCache(BANK_ACCOUNT_INFO_LOGO, qFilter);
            } else {

            }
            //数据填装
            this_dy.set("enable","1");
            this_dy.set("status","C");
            this_dy.set("bankaccountnumber", check(bankaccountnumber, "DEFAULT"));
            this_dy.set("name", check(name, "DEFAULT") + (null == bankaccountnumber ? "xxxx" : ((String)bankaccountnumber).substring(((String)bankaccountnumber).length()-4,((String)bankaccountnumber).length())));
            this_dy.set("englishname", check(englishname, "DEFAULT"));
            this_dy.set("company", check(company, ORG_LOGO, "number"));
            this_dy.set("openorg", check(openorg, ORG_LOGO, "number"));
//            this_dy.set("currency", check(currency, CURRENCY_LOGO, "number"));
            this_dy.set("finorgtype", check(finorgtype, "FINORGTYPE"));
            this_dy.set("acctproperty", check(acctproperty, "ACCTPURPOSE"));
            this_dy.set("bank", check(bank, FINORGINFO_LOGO, "name"));
            this_dy.set("acctstyle", check(acctstyle, "ACCTSTYLE"));
            this_dy.set("accttype", check(accttype, "ACCTTYPE"));
            this_dy.set("acctstatus", check(acctstatus, "ACCTSTATUS"));
            this_dy.set("closedate", check(closedate, "TIMER"));
            this_dy.set("closereason", check(closereason, "DEFAULT"));
            this_dy.set("opendate", check(opendate, "TIMER"));
//            this_dy.set("settlementtype", );
            this_dy.set("defaultcurrency", check(StringUtils.isEmpty((String)defaultcurrency)?"CNY":defaultcurrency, CURRENCY_LOGO, "number"));
            this_dy.set("comment", check(comment, "DEFAULT"));
            this_dy.set("shortnumber", check(shortnumber, "DEFAULT"));
//            this_dy.set("manager", check(manager, USER_LOGO, "number"));
//            this_dy.set("managecurrency", check(managecurrency, CURRENCY_LOGO, "number"));
//            this_dy.set("acctmanageamt", check(acctmanageamt, "DEFAULT"));
//            this_dy.set("isdefaultrec", check(isdefaultrec, "DEFAULT"));
//            this_dy.set("isdefaultpay", check(isdefaultpay, "DEFAULT"));
//            this_dy.set("strategy", check(strategy, STRATEGY_LOGO, "number"));
            this_dy.set("spic_issetbank", check(issetbankinterface, "DEFAULT"));
            this_dy.set("bebankfunc", check(bebankfunc, "BEBANKFUNC"));
            this_dy.set("spic_isopenbank", check(isopenbank, "DEFAULT"));
            this_dy.set("bankfunc", check(bankfunc, "BANKFUNC"));
            this_dy.set("acctname", check(acctname, "DEFAULT"));
            this_dy.set("bankinterface", check(bankinterface, "DEFAULT"));
            this_dy.set("netbank", check(netbank, NETBANKACCT_LOGO, "number"));

            this_dy.set("spic_source", "司库");

            this_dy.set("issetbankinterface", false);
            this_dy.set("isopenbank", false);

            Map<String, String> currencyMap = new HashMap<>();

            DynamicObjectCollection currencylistEntry = this_dy.getDynamicObjectCollection("spic_currencylist");
            DynamicObjectCollection currencyEntry = this_dy.getDynamicObjectCollection("currency");
            DynamicObjectCollection settlementtypeEntry = this_dy.getDynamicObjectCollection("settlementtype");
            currencylistEntry.clear();
            currencyEntry.clear();
            settlementtypeEntry.clear();
            List<DynamicObject> currencylistEntry_value = new ArrayList<>();
            DynamicObjectType type = currencylistEntry.getDynamicObjectType();
            DynamicObjectType currencyType = currencyEntry.getDynamicObjectType();
            List<DynamicObject> currencylistDys = new ArrayList<>();
            currencylist.forEach(item -> {
                String[] currencyKey = new String[3];
                item.entrySet().stream().forEach(m -> {
                    if ("currency".equals(m.getKey())) {
                        currencyKey[0] = m.getValue().toString();
                    } else if ("currencystatus".equals(m.getKey())) {
                        currencyKey[1] = m.getValue().toString();
                    } else if ("currencyAccountname".equals(m.getKey())) {
                        currencyKey[2] = m.getValue().toString();
                    }
                });
                DynamicObject currencylistDy = BusinessDataServiceHelper.loadSingle(CURRENCY_LOGO, "", new QFilter[]{new QFilter("number", QCP.equals, currencyKey[0])});
                DynamicObject curDy = new DynamicObject(currencyType);
                curDy.set("fbasedataid", currencylistDy);
                currencyEntry.add(curDy);
                DynamicObject dy = new DynamicObject(type);
                dy.set("spic_entry_currency", currencylistDy);
                dy.set("spic_currency_status", currencyKey[1]);
                dy.set("spic_account_name", currencyKey[2]);
                currencylistEntry.add(dy);
            });
            DynamicObjectType settlementtypeType = settlementtypeEntry.getDynamicObjectType();
            DynamicObject[] settlementtype_dys = BusinessDataServiceHelper.load(SETTLEMENTTYPE_LOGO,"",new QFilter[]{new QFilter("enable",QCP.equals,true)});
            Arrays.stream(settlementtype_dys).forEach(settlementtype_dy->{
                DynamicObject settlementtypeDy = new DynamicObject(settlementtypeType);
                settlementtypeDy.set("fbasedataid",settlementtype_dy);
                settlementtypeEntry.add(settlementtypeDy);
            });
            if (null == this_dy.getDynamicObject("company") || null == this_dy.getDynamicObject("openorg")){
                apiResult.setSuccess(false);
                apiResult.setMessage("请检查是否填写开户公司、申请公司或者开户公司、申请公司是否双方是否已经同步！！");
                LogBillUtils.modifyLog(logResult, "2", "请检查是否填写开户公司、申请公司或者开户公司、申请公司是否双方是否已经同步！！", "司库");
                LogUtils.log(false, "accountInfo", "传输失败", params.toString(), "请检查是否填写开户公司、申请公司或者开户公司、申请公司是否双方是否已经同步！！", startDate, null);
                return apiResult;
            }
//            this_dy.set("currency", currencylistDys.toArray());
//            DynamicObjectType type = currencyliissetbankinterfacet(type);
//            dy.set("spic_entry_currency",);
//            currencylistEntry.addAll(currencylistEntry_value);
//            currencyEntry.addAll(currencyEntry);
            OperationResult opResult = OperationServiceHelper.executeOperate("save", BANK_ACCOUNT_INFO_LOGO, new DynamicObject[]{this_dy}, OperateOption.create());
//            if (opResult.getAllErrorOrValidateInfo())
            List<IOperateInfo> errorMsg = opResult.getAllErrorOrValidateInfo();
            apiResult.setSuccess(opResult.isSuccess());
//            apiResult.setData();
            String errorStr = "";
            errorStr = StringUtils.join(errorMsg.stream().map(IOperateInfo::getMessage).toArray(), '|');
            apiResult.setMessage(errorStr);
//            apiResult.setErrorCode();
            LogBillUtils.modifyLog(logResult, "2", errorStr, "司库");
            LogUtils.log(opResult.isSuccess(), "accountInfo", "传输成功", params.toString(), errorStr, startDate, null);
            return apiResult;
        } catch (Exception e) {
            LogUtils.log(false, "accountInfo", "其他错误", params.toString() != null ? params.toString() : "", "", startDate,
                    e);
            LogBillUtils.modifyLog(logResult, "2", ThrowableUtil.stackTraceToString(e), "司库");
//            e.printStackTrace();
            apiResult.setSuccess(false);
            apiResult.setMessage(ThrowableUtil.stackTraceToString(e));
            return apiResult;
        }
    }

    /**
     * 校验数据
     *
     * @param obj
     * @return
     */
    public Object check(Object obj, String type) throws ParseException {
        if (null != obj && StringUtils.isNotEmpty((String)obj)) {
                if("TIMER".equals(type)) {
                    return timer(obj);
                }else if ("DEFAULT".equals(type)) {
                    return obj;
                }else if ("FINORGTYPE".equals(type)) {
                if ("1".equals(obj.toString())) {
                    return "0";
                } else if ("2".equals(obj.toString())) {
                    return "3";
                } else if ("3".equals(obj.toString())) {
                    return "4";
                } else {}
                } else if ("ACCTSTYLE".equals(type)) {
                    if ("1".equals(obj.toString())) {
                        return "basic";
                    } else if ("2".equals(obj.toString())) {
                        return "normal";
                    } else if ("3".equals(obj.toString())) {
                        return "temp";
                    } else if ("4".equals(obj.toString())) {
                        return "spcl";
                    } else if ("5".equals(obj.toString())) {
                        return "fgn_curr";
                    } else if ("6".equals(obj.toString())) {
                        return "fng_fin";
                    } else {
                    }
                }else if("ACCTTYPE".equals(type)) {
                    if ("1".equals(obj.toString())) {
                        return "in_out";
                    } else if ("2".equals(obj.toString())) {
                        return "in";
                    } else if ("3".equals(obj.toString())) {
                        return "out";
                    } else {
                        return "in_out";
                    }
                }else if("ACCTSTATUS".equals(type)) {
                    if ("1".equals(obj.toString())) {
                        return "normal";
                    } else if ("2".equals(obj.toString())) {
                        return "closing";
                    } else if ("3".equals(obj.toString())) {
                        return "closed";
                    } else if ("4".equals(obj.toString())) {
                        return "stop";
                    } else if ("5".equals(obj.toString())) {
                        return "frozen";
                    } else {
                    }
                }else if ("BEBANKFUNC".equals(type)) {
                    if ("1".equals(obj.toString())) {
                        return "query";
                    } else if ("2".equals(obj.toString())) {
                        return "pay";
                    } else if ("3".equals(obj.toString())) {
                        return "receipt";
                    } else if ("4".equals(obj.toString())) {
                        return "ecd";
                    } else {
                    }
                }else if ("BANKFUNC".equals(type)) {
                    if ("1".equals(obj.toString())) {
                        return "query";
                    } else if ("2".equals(obj.toString())) {
                        return "trans";
                    } else if ("3".equals(obj.toString())) {
                        return "invest";
                    } else if ("4".equals(obj.toString())) {
                        return "ecd";
                    } else {
                    }
                }else if("ACCTPURPOSE".equals(type)){
                    //默认为基本户
                    return BusinessDataServiceHelper.loadSingleFromCache(ACCTPURPOSE_LOGO,new QFilter[]{new QFilter("name",QCP.equals,StringUtils.isBlank(obj.toString())?"基本户":obj.toString())});
                }else{}
            return obj;
        }
        return null;
    }

    /**
     * 校验数据
     *
     * @param obj
     * @param entityNumber
     * @return
     */
    public Object check(Object obj, String entityNumber, String name) {
        QFilter qFilter[] = new QFilter[]{new QFilter(name, QCP.equals, obj)};
        return BusinessDataServiceHelper.loadSingle(entityNumber, name, qFilter);
    }


    /**
     * 时间格式
     *
     * @param obj
     * @return
     */
    public Date timer(Object obj) throws ParseException {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");// 格式化时间
        return sdf.parse(obj.toString());
    }
}
