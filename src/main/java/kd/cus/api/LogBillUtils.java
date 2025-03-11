package kd.cus.api;

import java.text.SimpleDateFormat;
import java.util.Date;

import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.CodeRuleServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;

public class LogBillUtils {
private static String LOGLIST_BILLCODE = "spic_maindata_log";// 日志的基础资料编码
	
	/**
	 * 开始发送log日志，进行开始记录
	 * @param request 请求
	 * @param response 响应
	 * @param source 来源
	 * @param partnerid 伙伴唯一id
	 * @param interfacename 接口名称
	 * @return
	 */
	public static LogEntity createLog(String request, String response, String source,String partnerid,String interfacename) {
		SimpleDateFormat sdf = new SimpleDateFormat();// 格式化时间 
        sdf.applyPattern("yyyy-MM-dd HH:mm:ss a");// a为am/pm的标记  
		// 将开始信息发送到存储log的苍穹单据列表里面
        Date startDate = new Date();
		Long orgId = 0L;//RequestContext.get().getOrgId();
		DynamicObject dynamicObject = BusinessDataServiceHelper.newDynamicObject(LOGLIST_BILLCODE);
//		Object logPkid = dynamicObject.getPkValue();
		String logId = CodeRuleServiceHelper.getNumber(LOGLIST_BILLCODE, dynamicObject, String.valueOf(orgId));
		dynamicObject.set("number", logId);
		dynamicObject.set("enable", "1");
		dynamicObject.set("status", "C");
		dynamicObject.set("spic_maindata_state", "0");
		dynamicObject.set("spic_start_time", null == startDate?"":sdf.format(startDate));
		dynamicObject.set("spic_end_time", "");
		dynamicObject.set("spic_over_time", "");
		dynamicObject.set("spic_interface_name", interfacename);
		dynamicObject.set("spic_source", source);
		dynamicObject.set("spic_partnerid", partnerid);
		dynamicObject.set("spic_requestdata_txt_tag", request);
		dynamicObject.set("spic_responsedata_txt_tag", response);
		
		try {	Object[] result = SaveServiceHelper.save(new DynamicObject[] {dynamicObject});
		
		}catch (Exception e) {
			e.printStackTrace();
		}
	
		
		
		return new LogEntity(logId, startDate);
	}
	
	/**
	 * 结束处理修改log日志
	 * @param logResult create函数返回值 包含日志id，以及创建日志时间
	 * @param state 状态 0开始 1正常 2出错
	 * @param response 响应
	 * @param source 来源
	 * @return
	 */
	public static String modifyLog(LogEntity logResult, String state, String response,String source) {
		SimpleDateFormat sdf = new SimpleDateFormat();// 格式化时间 
        sdf.applyPattern("yyyy-MM-dd HH:mm:ss a");// a为am/pm的标记  
		// 运行结束，将结果对开始的log日志进行修改
		if (null == logResult) {
			return "logResult不能为空";
		} else {
			Date endDate = new Date();
			String overTime = LogUtils.getOverTime(logResult.getNowDate(), endDate);
			QFilter qFilter = new QFilter("number", QCP.equals, logResult.getLogPkid());
			DynamicObject dynamicObject = BusinessDataServiceHelper.loadSingle(LOGLIST_BILLCODE, "enable,status,spic_maindata_state,spic_start_time,spic_end_time,spic_over_time,spic_interface_name,spic_source,spic_partnerid,spic_requestdata_txt_tag,spic_responsedata_txt_tag", new QFilter[] {qFilter});
			dynamicObject.set("enable", "1");
			dynamicObject.set("status", "C");
			//dynamicObject.set("number", logId);
			dynamicObject.set("spic_maindata_state", state);
			dynamicObject.set("spic_start_time", sdf.format(logResult.getNowDate()));
			dynamicObject.set("spic_end_time", sdf.format(endDate));
			dynamicObject.set("spic_over_time", overTime);
			//dynamicObject.set("spic_interface_name", funName);
			dynamicObject.set("spic_source", source);
//			dynamicObject.set("spic_partnerid", partnerid);
			//dynamicObject.set("spic_requestdata_txt_tag", request);
			dynamicObject.set("spic_responsedata_txt_tag", response);
			//OperationServiceHelper.executeOperate("save", LOGLIST_BILLCODE, new DynamicObject[] {dynamicObject}, OperateOption.create());
		
		    Object[] result = SaveServiceHelper.save(new DynamicObject[] {dynamicObject});
			
		
			return null;
		}
	}
	
}
