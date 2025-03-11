package kd.cus.api;

import com.alibaba.fastjson.JSON;
import kd.bos.bill.IBillWebApiPlugin;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.utils.StringUtils;
import kd.bos.entity.api.ApiResult;
import kd.bos.entity.operate.result.OperateErrorInfo;
import kd.bos.logging.Log;
import kd.bos.logging.LogFactory;
import kd.bos.mservice.webapi.OperationWebApi;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.service.ServiceFactory;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.cus.common.LogBillUtils;
import kd.cus.common.LogEntity;
import kd.cus.common.ThrowableUtils;

import static org.hamcrest.CoreMatchers.nullValue;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 收款单信息接收接口（司库）
 *
 * @author Wu Yanqi
 */
public class RecBillWebApi implements IBillWebApiPlugin {
	@Override
	public ApiResult doCustomService(Map<String, Object> params) {
		// 向后台monitor打印
		Log logBack = LogFactory.getLog(RecBillWebApi.class);
		logBack.info("RecBillWebApi被调用，入参为：" + params.toString());
		// 往单据中保存日志（前台日志）
		LogEntity logFront = LogBillUtils.createLog(params.toString(), null, "WebApi", "cas_recbill", "RecBillWebApi");
		List<RecBillResultEntity> resultEntities = new ArrayList<>();
		try {
			List<Map<String, Object>> datas = (List<Map<String, Object>>) params.get("datas");
			List<Map<String, Object>> dataPassValidateMustIn = new ArrayList<>();
			// 1. 根据billno校验数据已经存在
			List<Map<String, Object>> datasNotExist = new ArrayList<>();
			List<RecBillResultEntity> resultValidateExist = doValidateIfExist(datas, datasNotExist);
			resultEntities.addAll(resultValidateExist);
			// 2. 处理数据库中不存在的数据，校验必填项，日期转换校验
			for (Map<String, Object> data : datasNotExist) {
				RecBillResultEntity validateResult = doValidate(data);
				// 校验通过，构建新的数据
				if (validateResult == null) {
					dataPassValidateMustIn.add(data);
				} else {
					resultEntities.add(validateResult);
				}
			}
			List<String> billnos = new ArrayList<>();
			dataPassValidateMustIn.forEach((item) -> {
				billnos.add((String) item.get("billno"));
			});
			// 3. 业务处理
			doBizValidate(dataPassValidateMustIn);
			// 4. 处理最终数据，准备执行save
			for (Map<String, Object> data : dataPassValidateMustIn) {
				// 收款类型设置为“其他”
				Map<String, Object> receivingtype = new HashMap<>();
				receivingtype.put("number", "999");
				data.put("receivingtype", receivingtype);
				// 付款人类型设置为“其他”
				data.put("payertype", "other");
				// 设置单据类型为“OtherRec”
				data.put("biztype", "OtherRec");
				
				
				// //如果应收金额中没有值，就直接把收款金额赋值非应收金额
				// 处理分录数据
				// 取数	
				List<Map<String, Object>> entry = new ArrayList<Map<String, Object>>();
				
				
				List<Map<String, Object>> isentry = (List<Map<String, Object>>) (data.get("entry"));
				if (null != isentry && isentry.size() > 0) {// 如果分录存在
				//if ( data.get("entry"))) {// 如果有分录，不做任何处理
					for (Map<String, Object> item : isentry) {
						System.out.print("123");
						if (StringUtils.isBlank(item.get("e_receivableamt"))) {
							item.put("e_receivableamt", data.get("actrecamt"));
						}
					}
				} else {// 如果没有分录
					Map<String, Object> e_entry = new HashMap<>();
					e_entry.put("e_receivableamt", data.get("actrecamt"));
					entry.add(e_entry);
					data.put("entry", entry);
				}
				//---------------------------
				
			}
			params.put("datas", dataPassValidateMustIn);
			logBack.info("RecBillWebApi被调用，重新构造的保存参数：" + params.toString());
			// 校验通过，可以调用保存操作
			if (dataPassValidateMustIn.size() > 0) {
				Map<String, Object> executeSaveResult = ServiceFactory.getService(OperationWebApi.class)
						.executeOperation("cas_recbill", "save", params);
				logBack.info("RecBillWebApi被调用，保存操作的执行结果：" + executeSaveResult.toString());
				if (Boolean.valueOf(executeSaveResult.get("success").toString())) {
					for (Map<String, Object> data : dataPassValidateMustIn) {
						resultEntities.add(RecBillResultEntity.PROCESS_SUCCESS(data.get("billno").toString()));
					}
				} else {
					// API执行失败的情况，对结果进行解析
					List<Map<String, Object>> resData = (List<Map<String, Object>>) executeSaveResult.get("data");
					if (resData != null && resData.size() > 0) {
						for (Map<String, Object> info : resData) {
							if (Boolean.valueOf(info.get("success").toString())) {
								resultEntities.add(RecBillResultEntity
										.PROCESS_SUCCESS(billnos.get(Integer.valueOf(info.get("dindex").toString()))));
								continue;
							}
							Map<Integer, Object> convertResult = (Map<Integer, Object>) info.get("convertResult");
							if (convertResult != null && convertResult.size() > 0) {
								resultEntities.add(RecBillResultEntity.PROCESS_ERROR(
										billnos.get(Integer.valueOf(info.get("dindex").toString())),
										convertResult.get(0).toString()));
							} else {
								List<OperateErrorInfo> data = (List<OperateErrorInfo>) info.get("data");
								resultEntities.add(RecBillResultEntity.PROCESS_ERROR(
										billnos.get(Integer.valueOf(info.get("dindex").toString())),
										data.get(0).getMessage()));
							}
						}
					}
				}
			}
		} catch (Exception e) {
			logBack.error("RecBillWebApi被调用，执行保存主体出错，错误信息为：" + ThrowableUtils.getStackTrace(e));
			resultEntities.add(RecBillResultEntity.PROCESS_ERROR("", "其他错误，请查看错误日志或检查数据格式是否正确！"));
		}
		// 构造返回结果
		ApiResult apiResult = new ApiResult();
		List<Map<String, Object>> billResults = new ArrayList();
		apiResult.setSuccess(true);
		for (RecBillResultEntity entity : resultEntities) {
			if (!entity.getSuccess()) {
				apiResult.setSuccess(false);
			}
		}
		for (RecBillResultEntity entity : resultEntities) {
			billResults.add(RecBillResultEntity.toMap(entity));
		}
		apiResult.setData(billResults);

		LogBillUtils.modifyLog(logFront, "1", JSON.toJSONString(apiResult), "WebApi");

		// 返回
		return apiResult;
	}

	/**
	 * 业务校验
	 *
	 * @param dataPassValidateMustIn
	 * @return
	 */
	private void doBizValidate(List<Map<String, Object>> dataPassValidateMustIn) {
		//
		Map<String, Map<String, Object>> maps = new HashMap<>(); // 字段名：<billno, value>
		// recAccount
		Map<String, Object> recAccoungNameMaps = new HashMap<>();
		// actpayaccount 接收的是账号
		Map<String, Object> actpayaccountNumMaps = new HashMap<>();
		// 币别
		Map<String, Object> currencyNumMaps = new HashMap<>();
		for (Map<String, Object> data : dataPassValidateMustIn) {
			if (StringUtils.isNotBlank((String) data.get("actpayaccount"))) {
				actpayaccountNumMaps.put((String) data.get("billno"), (String) data.get("actpayaccount"));
			}
			recAccoungNameMaps.put((String) data.get("billno"), (String) data.get("recAccount"));
			currencyNumMaps.put((String) data.get("billno"), (String) data.get("currency"));

			// 处理币别
			String currencyNum = (String) data.get("currency");
			Map<String, String> currency = new HashMap<>();
			currency.put("number", currencyNum);
			data.put("currency", currency);
			// 处理本位币
			String basecurrencyNum = (String) data.get("basecurrency");
			Map<String, String> basecurrency = new HashMap<>();
			basecurrency.put("number", basecurrencyNum);
			data.put("basecurrency", basecurrency);
			// 结算方式
			String settletypeNum = (String) data.get("settletype");
			if (StringUtils.isNotBlank(settletypeNum)) {
				Map<String, String> settletype = new HashMap<>();
				settletype.put("number", settletypeNum);
				data.put("settletype", settletype);
			}
			// 审核人
			Object auditorObj = data.get("auditor");
			if (auditorObj != null) {
				Map<String, String> auditor = new HashMap<>();
				auditor.put("number", data.get("auditor").toString());
				data.put("auditor", auditor);
			}
			// 汇率表 exratetable
			String exratetableNum = (String) data.get("exratetable");
			if (StringUtils.isNotBlank(exratetableNum)) {
				Map<String, String> exratetable = new HashMap<>();
				exratetable.put("number", data.get("exratetable").toString());
				data.put("exratetable", exratetable);
			}

			// 处理分录数据
			List<Map<String, Object>> entry = (List<Map<String, Object>>) (data.get("entry"));
			if (null != entry && entry.size() > 0) {// 如果分录存在
				entry.forEach(item -> {
					// 资金用途 e_fundflowitem
					String e_fundflowitemNum = (String) item.get("e_fundflowitem");
					Map<String, String> e_fundflowitem = new HashMap<>();
					e_fundflowitem.put("number", e_fundflowitemNum);
					item.put("e_fundflowitem", e_fundflowitem);
					// 项目 project
					String projectNum = (String) item.get("project");
					Map<String, String> project = new HashMap<>();
					project.put("number", projectNum);
					item.put("project", project);
					// 费用项目 e_expenseitem_number
					String e_expenseitem_number = (String) item.get("e_expenseitem_number");
					Map<String, String> e_expenseitem = new HashMap<>();
					e_expenseitem.put("number", e_expenseitem_number);
					item.put("e_expenseitem", e_expenseitem);
					// 物料 e_material
					String e_materialNum = (String) item.get("e_material_number");
					Map<String, String> e_material = new HashMap<>();
					e_material.put("number", e_materialNum);
					item.put("e_material", e_material);
					// 实际收款公司 realreccompany

					String realreccompanyNum = (String) item.get("realreccompany");
					Map<String, String> realreccompany = new HashMap<>();
					realreccompany.put("number", realreccompanyNum);
					item.put("realreccompany", realreccompany);
				});
			}

		}
		maps.put("recAccount", recAccoungNameMaps);
		maps.put("actpayaccount", actpayaccountNumMaps);
		maps.put("currency", currencyNumMaps);

		Set<Object> accounts = new HashSet<>();
		accounts.addAll(maps.get("recAccount").values());
		accounts.addAll(maps.get("actpayaccount").values());

		DynamicObjectCollection queryRecAccountResult = QueryServiceHelper.query("bd_accountbanks",
				"id,bankaccountnumber,openorg.id",
				new QFilter[] { new QFilter("bankaccountnumber", QCP.in, accounts) });
		Map<String, String> bankaccountnumberIds = new HashMap<>(); // <name, id>
		Map<String, String> bankaccountnumberOpenorgIds = new HashMap<>();
		queryRecAccountResult.stream().forEach(dy -> {
			bankaccountnumberIds.put(dy.getString("bankaccountnumber"), dy.getString("id"));
			bankaccountnumberOpenorgIds.put(dy.getString("bankaccountnumber"), dy.getString("openorg.id"));
		});
		dataPassValidateMustIn.forEach(map -> {
			String id1 = bankaccountnumberIds.get(map.get("recAccount"));
			Map<String, String> accountbank = new HashMap();
			accountbank.put("id", id1);
			map.put("accountbank", accountbank);
			// 分录设置结算组织
			if (map.get("accountbank") != null) {
				List<Map<String, Object>> entry = (List<Map<String, Object>>) map.get("entry");
				if (entry != null && entry.size() > 0) {
					entry.forEach(item -> {
						Map<String, String> e_settleorg = new HashMap<>();
						e_settleorg.put("id", bankaccountnumberOpenorgIds.get(map.get("recAccount")));
						item.put("e_settleorg", e_settleorg);
					});
				}
			}

			String id2 = bankaccountnumberIds.get(map.get("actpayaccount"));
			Map<String, String> bankaccountnumber = new HashMap();
			bankaccountnumber.put("id", id2);
			map.put("actpayaccount", accountbank);
		});

	}

	/**
	 * 校验数据是否已经存在
	 *
	 * @param datas
	 * @param datasNotExist
	 * @return
	 */
	private List<RecBillResultEntity> doValidateIfExist(List<Map<String, Object>> datas,
			List<Map<String, Object>> datasNotExist) {
		List<RecBillResultEntity> resultValidateExist = new ArrayList<>();
		List<String> billnos = new ArrayList<>();
		for (Map<String, Object> data : datas) {
			if (StringUtils.isNotBlank(data.get("billno").toString())) {
				billnos.add(data.get("billno").toString());
			}
		}
		DynamicObjectCollection queryResult = QueryServiceHelper.query("cas_recbill", "billno",
				new QFilter[] { new QFilter("billno", QCP.in, billnos) });
		List<String> existBillnos = new ArrayList<>();
		for (DynamicObject dyObj : queryResult) {
			RecBillResultEntity recBillResultEntity = RecBillResultEntity.PROCESS_ERROR(dyObj.getString("billno"),
					"收款单数据已存在！");
			existBillnos.add(dyObj.getString("billno"));
			resultValidateExist.add(recBillResultEntity);
		}
		for (Map<String, Object> data : datas) {
			if (!existBillnos.contains(data.get("billno").toString())) {
				datasNotExist.add(data);
			}
		}
		return resultValidateExist;
	}

	/**
	 * 字段必填校验，日期转换校验
	 *
	 * @param data
	 * @return
	 */
	private RecBillResultEntity doValidate(Map<String, Object> data) {
		StringBuilder message = new StringBuilder();
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
		// 对值的校验操作
		String billno = (String) data.get("billno");
		if (StringUtils.isBlank(billno)) {
			message.append("司库系统单据编号是必填字段;");
		}
		String source_id = (String) data.get("source_id");
		if (StringUtils.isBlank(source_id)) {
			message.append("司库系统收款ID是必填字段;");
		} else {
			data.put("spic_source_id", data.get("source_id"));
			data.remove("source_id");
		}
		// 司库系统对应付款ID
		String source_pay_id = (String) data.get("source_pay_id");
		if (StringUtils.isBlank(source_pay_id)) {
			message.append("司库系统对应付款ID是必填字段;");
		} else {
			data.put("spic_source_pay_id", data.get("source_pay_id"));
			data.remove("source_pay_id");
		}
		// 业务日期
		String bizdate = (String) data.get("bizdate");
		if (StringUtils.isBlank(bizdate)) {
			message.append("业务日期是必填字段;");
		} else {
			try {
				Date parse = dateFormat.parse(bizdate);
			} catch (ParseException e) {
				e.printStackTrace();
				message.append("业务日期转换失败，请检查格式yyyy-MM-dd;");
			}
		}
		// 付款人名称
		String payername = (String) data.get("payername");
		if (StringUtils.isBlank(payername)) {
			message.append("付款人名称是必填字段;");
		}
		// 收款账号
		String recAccount = (String) data.get("recAccount");
		if (StringUtils.isBlank(recAccount)) {
			message.append("收款账号是必填字段;");
		}
		// 收款户名，根据收款户名查询“银行账户”并赋值给“accountbank”
		String spic_recAccountName = (String) data.get("recAccountName");
		if (StringUtils.isBlank(spic_recAccountName)) {
			message.append("收款户名是必填字段;");
		}
		// 收款开户行名称
		String recOpenBankName = (String) data.get("recOpenBankName");
		if (StringUtils.isBlank(recOpenBankName)) {
			message.append("收款开户行名称是必填字段;");
		}
		// 收款开户行行号
		String recOpenBankNo = (String) data.get("recOpenBankNo");
		if (StringUtils.isBlank(recOpenBankNo)) {
			message.append("收款开户行行号是必填字段;");
		}
		// 收款所属行 工商银行bd_finorginfo
		String recSubBankName = (String) data.get("recSubBankName");
		if (StringUtils.isBlank(recSubBankName)) {
			message.append("收款所属行是必填字段;");
		}
		// 币别
		Object currency = data.get("currency");
		if (currency == null) {
			message.append("币别是必填字段;");
		}
		// 汇率日期
		String exratedate = (String) data.get("exratedate");
		if (StringUtils.isNotBlank(exratedate)) {
			try {
				Date parse = dateFormat.parse(exratedate);
			} catch (ParseException e) {
				e.printStackTrace();
				message.append("汇率日期转换失败，请检查格式yyyy-MM-dd;");
			}
		}
		// 汇率 数值类型
		Object exchangerate = data.get("exchangerate");
		if (exchangerate == null || !(exchangerate instanceof Number)) {
			message.append("汇率是必填字段;");
		}
		// 收款金额
		Object actrecamt = data.get("actrecamt");
		if (actrecamt == null || !(actrecamt instanceof Number)) {
			message.append("收款金额是必填字段;");
		}
		// 折本位币
		Object localamt = data.get("localamt");
		if (localamt == null || !(localamt instanceof Number)) {
			message.append("折本位币是必填字段;");
		}
		// 其他值转化映射

		if (StringUtils.isNotBlank(message.toString())) {
			return RecBillResultEntity.PROCESS_ERROR(billno, message.toString());
		}
		return null;
	}
}
