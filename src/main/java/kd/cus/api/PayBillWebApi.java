package kd.cus.api;

import static org.hamcrest.CoreMatchers.nullValue;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import json.JSON;
import kd.bos.bill.IBillWebApiPlugin;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.serialization.SerializationUtils;
import kd.bos.entity.api.ApiResult;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.entity.validate.BillStatus;
import kd.bos.form.field.BillStatusEdit;
import kd.bos.mservice.form.OperationWebApiImpl;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.serverless.api.StatusEnum;
import kd.bos.service.business.datamodel.DynamicFormModelProxy;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.util.StringUtils;
import kd.bos.workflow.unittest.util.BusinessDataHelper;
import kd.fi.cas.helper.OrgHelper;
import kd.fi.iep.util.BussinessAndOperProvider;
import kd.taxc.tcvat.formplugin.account.UUID;

/**
 * 司库 接口描述：允许第三方系统通过API创建苍穹财务云的付款单数据
 * 功能说明：第三系统按照苍穹财务云的付款单JSON数据通过付款单创建API生成苍穹财务云的付款单数据 params 针对传入各项参数的处理 判断非空必录项
 * 必填项: 单据编号 billno 单据状态 billstatus 付款组织 org 业务日期 bizdate 单据类型billtype 结算方式
 * settletype 支付渠道 paymentchannel 付款类型 paymenttype 付款银行 payerbank 付款币别 currency
 * 收款人类型 payeetype 收款人类型 itempayeetype(隐藏) 收款人 itempayee(隐藏) 收款人名称payeename(隐藏)
 * 收款人实名 recaccbankname(隐藏) 收款人基础资料标识 payeeformid (隐藏) 收款人ID payee(隐藏)
 * 收款账户基础资料标识 payeeaccformid (隐藏) 实付金额 e_actamt 应付金额 e_payableamt 必填项
 * (是否需要扩展字段，保存司库的付款):司库系统单据编号source_bill_num 司库系统付款ID source_id 司库系统收款唯一ID
 * "billtype"://单据类型 paymenttype"://付款类型 "settletype"://结算方式 "org"://付款人 必录
 * "payeracctbank"://付款账号 必录 "payerbank"://付款银行 必录 "transtype"://交易种类
 * "currency"://付款币别 "dpcurrency"://异币别付款币别 "feecurrency"://手续费币别 "payeename":
 * "北京蒙牛有限公司", //收款人名称 必录 根据收款人名称---查询收款人ID---保存ID "dpexratetable"://异币别付款汇率表
 * "reccountry"://收款方国家地区 "feeactbank"://手续费账户 分录基础资料： "e_material"://物料
 * "settleorg"://结算组织 "e_expenseitem"://费用项目 "project"://项目
 * "e_fundflowitem"://资金用途
 * 
 * @author hdp
 * @author ZXR(修改)
 */
public class PayBillWebApi implements IBillWebApiPlugin {

	@Override
	public ApiResult doCustomService(Map<String, Object> params) {

		// 前台打印日志（往单据中写）
		LogEntity logResult = LogBillUtils.createLog(params.toString(), "", "司库", "", "payBill");
		// 后台打印日志
		Date startDate = LogUtils.log(null, "payBill", "开始传输", "", "", null, null);
		ApiResult result = new ApiResult();
		try {
			List datas = (ArrayList) params.get("datas");
			// 司库单据编码和付款唯一id 校验 进行司库付款唯一ID校验
			List sourceIDList = new ArrayList();// 司库系统付款ID
			List sourceIDRiList = new ArrayList();
			List sourceBillNumList = new ArrayList();// 司库系统单据编号
			List sourceBillNumRiList = new ArrayList();
			List rightDatas = new ArrayList();
			List wrongDatas = new ArrayList();
			// Map wrongDatasMap = new HashMap();
			Map rightDatasMap = new HashMap();
			List<String> errMsgList = new ArrayList();

			List listId = new ArrayList<>();
			List listBillId = new ArrayList<>();

			List res = new ArrayList<>();

			for (int i = 0; i < datas.size(); i++) {
				Map<String, Object> data = (HashMap<String, Object>) datas.get(i);
				sourceIDList.add(data.get("spic_source_id"));
				sourceBillNumList.add(data.get("spic_source_bill_num"));
			}
			// 查询 司库付款唯一编码是否存在 付款唯一ID--司库单据编码
			DynamicObjectCollection sourceIDQuery = QueryServiceHelper.query("cas_paybill",
					"id,billno,spic_source_id,spic_source_bill_num",
					new QFilter[] { new QFilter("spic_source_id", QCP.in, sourceIDList) });

			Map sourceIDMap = new HashMap();// 付款id、单据编号一一对应

			if (sourceIDQuery != null) {
				for (int i = 0; i < sourceIDQuery.size(); i++) {
					sourceIDMap.put(sourceIDQuery.get(i).get("spic_source_id"),
							sourceIDQuery.get(i).get("spic_source_bill_num"));
				}
			}

			for (int i = 0; i < datas.size(); i++) {
				StringBuilder errMsg = new StringBuilder();// 错误信息errMsg
				Map<String, Object> data = (HashMap<String, Object>) datas.get(i);

				// System.out.println(data);

				// 司库系统单据编号 必录-保存信息
				if (data.get("spic_source_bill_num") == null || "".equals(data.get("spic_source_bill_num"))) {
					errMsg.append("司库系统单据编码有误 请检查！");
				}
				// 司库系统付款ID 必录-保存信息
				if (data.get("spic_source_id") == null || "".equals(data.get("spic_source_id"))) {
					errMsg.append("司库系统付款ID信息有误 请检查！");
				} else {
					if (sourceIDMap.containsKey(data.get("spic_source_id"))) {
						errMsg.append("司库系统付款唯一ID已存在 请检查！");
						/*
						 * errMsgList.add(errMsg.toString()); Map wrongDatasMap = new HashMap();
						 * wrongDatasMap.put("spic_source_bill_num", data.get("spic_source_bill_num"));
						 * wrongDatasMap.put("spic_source_id", data.get("spic_source_id"));
						 * wrongDatasMap.put("errMsg", errMsg); wrongDatasMap.put("success", false);
						 * wrongDatas.add(wrongDatasMap); continue;
						 */
					} else {
						rightDatasMap.put("spic_source_bill_num", data.get("spic_source_bill_num"));
						rightDatasMap.put("spic_source_id", data.get("spic_source_id"));
						sourceIDRiList.add(data.get("spic_source_id"));
						sourceBillNumRiList.add(data.get("spic_source_bill_num"));

					}
				}
				// 单据状态校验 苍穹必录
				data.put("billstatus", "A");
				// 单据类型bos_billtype 苍穹必录
				if (data.get("billtype") != null) {
					String billtype = (String) data.get("billtype");
					// if (billtypeData.get("number") == null ||
					// "".equals(billtypeData.get("number"))) {
					if (billtype == null || "".equals(billtype)) {
						errMsg.append("单据类型信息有误或信息不存在 请检查！");
					}
					Map<String, Object> billtypeData = new HashMap<>();
					billtypeData.put("number", billtype);
					data.put("billtype", billtypeData);
				} else {
					// 单据类型默认为默认其他付款类型
					Map<String, Object> billtypeData = new HashMap<>();
					billtypeData.put("number", "cas_paybill_other_BT_S");
					data.put("billtype", billtypeData);
				}
				// 业务日期校验 必录
				if (data.get("bizdate") == null) {
					errMsg.append("单据业务日期信息有误或信息不存在 请检查！");
				}
				// 期待付款日期校验 可空
				// 付款类型 可空 苍穹必录
				// if (data.get("paymenttype") != null) {
				// String payment = (String) data.get("paymenttype");
				// //if (payeracctbankData.get("number") == null ||
				// "".equals(payeracctbankData.get("number"))) {
				// if (payment== null || "".equals(payment)) {
				// errMsg.append("单据付款类型信息有误或信息不存在 请检查！");
				// }
				//// Map<String, Object> paymenttypeData = new HashMap<>();
				//// paymenttypeData.put("number",999 );
				//// data.put("paymenttype", paymenttypeData);
				// }else {
				// 默认付款类型其他付款类型
				Map<String, Object> paymenttypeData = new HashMap<>();
				paymenttypeData.put("number", "999");
				data.put("paymenttype", paymenttypeData);

				// 内部账号 基础资料 置空
				data.put("inneraccount", null);

				// }
				// //内部账号 可空
				// if (data.get("inneraccount") != null) {
				// String billtypeData = (String) data.get("inneraccount");
				// //if (billtypeData.get("number") == null ||
				// "".equals(billtypeData.get("number"))) {
				// if (billtypeData == null || "".equals(billtypeData)) {
				// errMsg.append("内部账号信息有误或信息不存在 请检查！");
				// }
				// }
				// 收款人类型payeetype 当查询收款人账号时可以进行赋值 可空
				if (data.get("payeetype") != null) {
					if ("bos_org".equals(data.get("payeetype")) || "bd_supplier".equals(data.get("payeetype"))
							|| "bd_customer".equals(data.get("payeetype"))
							|| "bos_user".equals(data.get("payeetype"))) {
						data.put("payeetype", data.get("payeetype"));
					} else {
						data.put("payeetype", "other");
					}
				}
				// 结算方式settletype 可空
				if (data.get("settletype") != null) {
					// String settletypeData = (String) data.get("settletype");
					if ("JSFS04".equals(data.get("settletype")) || "1".equals(data.get("settletype"))) {
						Map<String, Object> settletypeData = new HashMap<>();
						settletypeData.put("number", "JSFS04");
						data.put("settletype", settletypeData);
					} else {
						data.put("settletype", null);
					}
				}

				// 结算号(文本类型)
				data.put("settletnumber", data.get("draftbill"));

				// 结算号(基础资料)
				data.put("draftbill", null);

				// if (data.get("draftbill") != null ||!"".equals(data.get("draftbill"))) {
				// Map<String, Object> draftbillData = new HashMap<>();
				// draftbillData.put("number", "");
				// data.put("draftbill", null);
				// }

				// 支付渠道paymentchannel 可空
				if (data.get("paymentchannel") != null) {
					if ("1".equals(data.get("paymentchannel"))) {
						data.put("paymentchannel", "bei");// 银企互联
					} else if ("2".equals(data.get("paymentchannel"))) {
						data.put("paymentchannel", "other");// 其他
					} else if ("3".equals(data.get("paymentchannel"))) {
						data.put("paymentchannel", "counter");// 柜台
					} else {
						errMsg.append("支付渠道信息有误或信息不存在 请检查！");
					}
				}

				// 是否跨境支付 /是否异币别付款 0 是true、 1否false 可空
				if (data.get("iscrosspay") != null && !data.get("iscrosspay").toString().isEmpty()) {
					if ("0".equals(data.get("iscrosspay").toString())||0==(Integer)data.get("iscrosspay")) {
						data.put("iscrosspay", true);
					} else if ("1".equals(data.get("iscrosspay"))||1==(Integer)data.get("iscrosspay")) {
						data.put("iscrosspay", false);
					} else {
						errMsg.append("是否跨境支付信息有误或信息不存在 请检查！");
					}
				}
				// 是否异币别付款 0 是、 1否
				if (data.get("isdiffcur") != null && !data.get("isdiffcur").toString().isEmpty()) {
					if ("0".equals(data.get("isdiffcur"))||0==(Integer)data.get("isdiffcur")) {
						data.put("isdiffcur", true);
					} else if ("1".equals(data.get("isdiffcur"))||1==(Integer)data.get("isdiffcur")) {
						data.put("isdiffcur", false);
					} else {
						errMsg.append("是否异币别付款信息有误或信息不存在 请检查！");
					}
				}

				// 付款人信息 付款户名，根据付款账号查询“银行账户” 查询付款银行 必录
				if (data.get("org") != null && !"".equals(data.get("org").toString())) {
					// String org = (String) data.get("org");
					// if (org == null || "".equals(org)) {
					// errMsg.append("付款人信息有误或信息不存在 请检查！");
					// }
					Map orgData = new HashMap<>();
					orgData.put("number", data.get("org"));
					data.put("org", orgData);
				} else {
					errMsg.append("付款人信息有误或信息不存在 请检查！");
				}

				// 付款银行 payerbankname 必录 payerbank
				// if (data.get("payerbank") != null &&
				// !"".equals(data.get("payerbank").toString())) {

				// 付款账号 payeracctbank苍穹字段 必录payerbanknum
				if (data.get("payerbanknum") != null && !"".equals(data.get("payerbanknum").toString())) {

					// 根据payerbanknum 取 payerbank 付款银行 ，必录 payerbank
					String payeracctbank = (String) data.get("payerbanknum");
					DynamicObjectCollection payerbankInfo = QueryServiceHelper.query("bd_accountbanks",
							"id,name,number,bank", new QFilter[] { new QFilter("number", QCP.equals, payeracctbank) });
					if (null != payerbankInfo && payerbankInfo.size() > 0) {
						DynamicObjectCollection bankInfo = QueryServiceHelper.query("bd_finorginfo", "id,name,number",
								new QFilter[] { new QFilter("id", QCP.equals, payerbankInfo.get(0).get("bank")) });
						if (null != bankInfo && bankInfo.size() > 0) {
							Map payerbankData = new HashMap();
							payerbankData.put("number", bankInfo.get(0).get("number"));
							data.put("payerbank", payerbankData);
						}
					}

					// 设置付款账号
					Map payeracctbankData = new HashMap();
					payeracctbankData.put("number", data.get("payerbanknum"));
					data.put("payerbanknum", payeracctbankData);
				} else {
					errMsg.append("付款账号信息有误或信息不存在 请检查！");
				}

				// 收款人 payeename 必录 其他
				if (data.get("payeename") == null) {
					errMsg.append("收款人信息有误或信息不存在 请检查！");
				}

				// 收款人实名 -- 收款银行 -- 收款行行号 必录
				if (data.get("recaccbankname") == null || data.get("recbanknumber") == null) {
					errMsg.append("收款方信息有误或信息不存在 请检查！");
				}

				// 收款银行 根据payeebanknum收款账号，获取收款银行
				if (data.get("payeebanknum") != null && !"".equals(data.get("payeebanknum").toString())) {
					String payeebankname = (String) data.get("payeebanknum");
					DynamicObjectCollection payeebankInfo = QueryServiceHelper.query("bd_accountbanks",
							"id,name,number,bank", new QFilter[] { new QFilter("number", QCP.equals, payeebankname) });
					if (null != payeebankInfo && payeebankInfo.size() > 0) {
						DynamicObjectCollection payeebank = QueryServiceHelper.query("bd_finorginfo", "id,name,number",
								new QFilter[] { new QFilter("id", QCP.equals, payeebankInfo.get(0).get("bank")) });
						if (null != payeebank && payeebank.size() > 0) {
							data.put("payeebankname", payeebank.get(0).get("name"));
						}
					}

					// Map payeeData = new HashMap();
					// payeeData.put("number",payeebank.get(0).get("number"));

				} else {
					errMsg.append("收款账号信息有误或信息不存在 请检查！");
				}

				// 增加收款方ID 根据收款方名称 + 收款类型 == 查询id 后台逻辑需保存数据
				DynamicObjectCollection payeeInfo = new DynamicObjectCollection();
				if (data.get("payeename") != null && !data.get("payeename").toString().isEmpty()) {
					String payeenameStr = (String) data.get("payeename");
					payeeInfo = QueryServiceHelper.query("bos_org", "id,number,name",
							new QFilter[] { new QFilter("name", QCP.equals, payeenameStr) });// 公司
					if (payeeInfo != null && payeeInfo.size() > 0) {
						data.put("payee", payeeInfo.get(0).get("id"));
						// if(data.get("payeetype").toString().isEmpty()||data.get("payeetype")==null) {
						data.put("payeetype", "bos_org");
						// }
					} else {
						payeeInfo = QueryServiceHelper.query("bd_supplier", "id,number,name",
								new QFilter[] { new QFilter("name", QCP.equals, payeenameStr) });// 供应商
						if (payeeInfo != null && payeeInfo.size() > 0) {
							data.put("payee", payeeInfo.get(0).get("id"));
							// if(data.get("payeetype").toString().isEmpty()||data.get("payeetype")==null) {
							data.put("payeetype", "bd_supplier");
							// }
						} else {
							payeeInfo = QueryServiceHelper.query("bd_customer", "id,number,name",
									new QFilter[] { new QFilter("name", QCP.equals, payeenameStr) });// 客户
							if (payeeInfo != null && payeeInfo.size() > 0) {
								data.put("payee", payeeInfo.get(0).get("id"));
								// if(data.get("payeetype").toString().isEmpty()||data.get("payeetype")==null) {
								data.put("payeetype", "bd_customer");
								// }
							} else {
								payeeInfo = QueryServiceHelper.query("bos_user", "id,number,name",
										new QFilter[] { new QFilter("name", QCP.equals, payeenameStr) });// 职员
								if (payeeInfo != null && payeeInfo.size() > 0) {
									data.put("payee", payeeInfo.get(0).get("id"));
									// if(data.get("payeetype").toString().isEmpty()||data.get("payeetype")==null) {
									data.put("payeetype", "bos_user");
									// }
								} else {
									data.put("payeetype", "other");
								}
							}
						}
					}
				} else {
					// 收款方信息有误
					errMsg.append("收款方信息有误或信息不存在 请检查！");
				}

				// 币别校验--"currency"://付款币别 "dpcurrency"://异币别付款币别 "feecurrency"://手续费币别 可空
				if (data.get("currency") != null) {
					// String settletypeData = (String) data.get("currency");
					Map currencyData = new HashMap();
					currencyData.put("number", data.get("currency"));
					data.put("currency", currencyData);
				}
				// "dpcurrency"://异币别付款币别
				if (data.get("dpcurrency") != null) {
					// String settletypeData = (String) data.get("currency");
					Map dpcurrencyData = new HashMap();
					dpcurrencyData.put("number", data.get("dpcurrency"));
					data.put("dpcurrency", dpcurrencyData);
				}
				// "feecurrency"://手续费币别
				if (data.get("feecurrency") != null) {
					// String settletypeData = (String) data.get("currency");
					Map feecurrencyData = new HashMap();
					feecurrencyData.put("number", data.get("feecurrency"));
					data.put("feecurrency", feecurrencyData);
				}
				// 付款汇率exchangerate 可空
				// 付款金额actpayamt 可空
				// 付款金额折本位币localamt 可空
				// 异币别付款汇率表dpexratetable 可空
				// if (data.get("dpexratetable") != null) {
				// Map payeracctbankData = (Map) data.get("dpexratetable");
				// if (payeracctbankData.get("number") == null ||
				// "".equals(payeracctbankData.get("number"))) {
				// errMsg.append("异币别付款汇率表信息有误或信息不存在 请检查！");
				// }
				// }
				// 异币别付款汇率日期dpexratedate 可空
				// 异币别付款汇率dpexchangerate 可空
				// 异币别付款金额dpamt 可空
				// 异币别付款金额折本位币dplocalamt 可空
				// 兑换合约号contractno 可空
				// 兑换汇率agreedrate 可空
				// 汇兑损益lossamt 可空

				// 转账附言 usage 支付渠道为银企互联时必填 收款方省recprovince收款方市县reccity支付渠道为银企互联时必填
				if (data.get("paymentchannel") != null && "bei".equals(data.get("paymentchannel").toString())) {
					// if("bei".equals(data.get("paymentchannel"))) {
					if (data.get("usage") == null) {
						errMsg.append("支付渠道为银企互联时 转账附言有误或信息不存在 请检查！");
					}
					if (data.get("recprovince") == null) {
						errMsg.append("支付渠道为银企互联时 收款方省有误或信息不存在 请检查！");
					}
					if (data.get("reccity") == null) {
						errMsg.append("支付渠道为银企互联时 收款方市县有误或信息不存在 请检查！");
					}
				}
				// 收款方地址recaddress 可空
				// 收款方邮箱recemail 可空
				// 收款方国家地区reccountry 可空
				if (data.get("reccountry") != null) {
					// String settletypeData = (String) data.get("currency");
					Map reccountryData = new HashMap();
					reccountryData.put("number", data.get("reccountry"));
					data.put("reccountry", reccountryData);
				}
				// 收款方省recprovince 可空
				// 收款方市县reccity 可空
				// 收款行地址recbankaddress 可空
				// 收款行SWFIT CODErecswiftcode 可空
				// 收款行ROUTING NUMBERrecroutingnum 可空
				// 收款行其他行号recothercode 可空
				// 手续费承担方feepayer 可空
				// 手续费账户feeactbank 可空
				if (data.get("feeactbank") != null) {
					// String settletypeData = (String) data.get("currency");
					Map feeactbankData = new HashMap();
					feeactbankData.put("number", data.get("feeactbank"));
					data.put("feeactbank", feeactbankData);
				}
				// 手续费币别feecurrency 可空
				// 手续费fee 可空
				// 清算要求参数auditparam 可空
				// 交易种类transtype 可空
				if (data.get("transtype") != null) {
					Map transtypeData = new HashMap();
					transtypeData.put("number", data.get("transtype"));
					data.put("transtype", transtypeData);
				}
				// 付款方式paymethod 可空
				// 服务级别serlevel 可空
				// 付款代理行payproxybank 可空
				if (data.get("payproxybank") != null) {
					Map payproxybankData = new HashMap();
					payproxybankData.put("number", data.get("payproxybank"));
					data.put("payproxybank", payproxybankData);
				}
				
				data.put("spic_source", "司库");
				// 支票类型checktype 可空
				// 寄送方式sendway 可空
				// 支票用途checkuse 可空
				// 实付金额 e_actamt 必录 应付金额 e_payableamt 必录
				List entryArray = (ArrayList) data.get("entry");
				if (entryArray != null) {
					for (int j = 0; j < entryArray.size(); j++) {
						Map<String, Object> entrydata = (HashMap<String, Object>) entryArray.get(j);
						if (entrydata.get("e_actamt") != null && entrydata.get("e_payableamt") != null) {
						} else {
							errMsg.append("付款明细中 实付金额或者应付金额信息有误 请检查！");
						}
						// 物料e_material
						if (entrydata.get("e_material") != null) {
							Map e_materialData = new HashMap();
							e_materialData.put("number", entrydata.get("e_material"));
							entrydata.put("e_material", e_materialData);
							// Map transtypeData = (Map) data.get("e_material");
							// if (transtypeData.get("number") == null ||
							// "".equals(transtypeData.get("number"))) {
							// errMsg.append("付款明细中 物料信息有误或信息不存在 请检查！");
							// }
						}
						// 结算组织
						if (entrydata.get("settleorg") != null) {
							Map settleorgData = new HashMap();
							settleorgData.put("number", entrydata.get("settleorg"));
							entrydata.put("settleorg", settleorgData);
							// Map transtypeData = (Map) data.get("settleorg");
							// if (transtypeData.get("number") == null ||
							// "".equals(transtypeData.get("number"))) {
							// errMsg.append("付款明细中 结算组织信息有误或信息不存在 请检查！");
							// }
						}
						// 费用项目er_expenseitemedit
						if (entrydata.get("e_expenseitem") != null) {
							Map e_expenseitemData = new HashMap();
							e_expenseitemData.put("number", entrydata.get("e_expenseitem"));
							entrydata.put("e_expenseitem", e_expenseitemData);
							// Map transtypeData = (Map) data.get("e_expenseitem");
							// if (transtypeData.get("number") == null ||
							// "".equals(transtypeData.get("number"))) {
							// errMsg.append("付款明细中 费用项目信息有误或信息不存在 请检查！");
							// }
						}
						// 项目bd_project
						if (entrydata.get("project") != null) {
							Map projectData = new HashMap();
							projectData.put("number", entrydata.get("project"));
							entrydata.put("project", projectData);
							// Map transtypeData = (Map) data.get("project");
							// if (transtypeData.get("number") == null ||
							// "".equals(transtypeData.get("number"))) {
							// errMsg.append("付款明细中 项目信息有误或信息不存在 请检查！");
							// }
						}
						// 资金用途cas_fundflowitem
						if (entrydata.get("e_fundflowitem") != null) {
							Map e_fundflowitemData = new HashMap();
							e_fundflowitemData.put("number", entrydata.get("e_fundflowitem"));
							entrydata.put("e_fundflowitem", e_fundflowitemData);
							// Map transtypeData = (Map) data.get("e_fundflowitem");
							// if (transtypeData.get("number") == null ||
							// "".equals(transtypeData.get("number"))) {
							// errMsg.append("付款明细中 资金用途信息有误或信息不存在 请检查！");
							// }
						}
					}
				}
				// errMsgList.add(errMsg.toString());
				if (errMsg.length() > 0) {
					errMsgList.add(errMsg.toString());
					Map wrongDatasMap = new HashMap();
					wrongDatasMap.put("spic_source_bill_num", data.get("spic_source_bill_num"));
					wrongDatasMap.put("spic_source_id", data.get("spic_source_id"));
					wrongDatasMap.put("errMsg", errMsg);
					wrongDatasMap.put("success", false);
					wrongDatas.add(wrongDatasMap);
					continue;
				} else {
					rightDatas.add(datas.get(i));
					listId.add(data.get("spic_source_id"));
					listBillId.add(data.get("spic_source_bill_num"));
				}

			}
			Map<String, Object> newParams = new HashMap();
			newParams.put("datas", rightDatas);
			String string123 = JSON.toJSONString(newParams);

			Map<String, Object> apiResultMap = new OperationWebApiImpl().executeOperation("cas_paybill", "save",
					newParams);
			// 遍历修改返回值
			List<Map<String, Object>> redatas = new ArrayList();
			List redataaa = new ArrayList();
			result = new ApiResult();
			List reDatas = (ArrayList) apiResultMap.get("data");
			if (reDatas != null) {
				for (int i = 0; i < reDatas.size(); i++) {
					Map<String, Object> reData = (HashMap<String, Object>) reDatas.get(i);
					reData.put("spic_source_id", listId.get(i));
					reData.put("spic_source_bill_num", listBillId.get(i));

					Map<Integer, Object> convertResult = (Map<Integer, Object>) reData.get("convertResult");

					if (convertResult != null && convertResult.size() > 0) {
						List list = (List) convertResult.get(0);
						reData.put("errMsg", list.get(0));
						reData.remove("convertResult");
					}
					redataaa.add(reData);
					
				}
				
				//redatas = (List<Map<String, Object>>) apiResultMap.get("data");
				
				//res.add(redataaa);
			
				// 日志
				LogBillUtils.modifyLog(logResult, "1", redataaa.toString(), "司库");
				LogUtils.log(true, "payBill", "传输成功", params.toString(), redataaa.toString(), startDate, null);
			}

			if (null != errMsgList || errMsgList.size() != 0) {
				List reDatas1 = new ArrayList<>();
				for (int i = 0; i < wrongDatas.size(); i++) {
					redataaa.add(wrongDatas.get(i));
				}
				//res.add(redataaa);
				// 日志
				LogBillUtils.modifyLog(logResult, "1", redataaa.toString(), "司库");
				LogUtils.log(true, "payBill", "传输成功", params.toString(), redataaa.toString(), startDate, null);
			}
			result.setData(redataaa);

		} catch (Exception e) {
			result.setSuccess(false);
			result.setErrorCode("执行异常-其他错误");
			result.setMessage("");
//			if() {
//				
//			}
			LogUtils.log(false, "payBill", "其他错误", params.toString() != null ? params.toString() : "", "", startDate,
					e);
			LogBillUtils.modifyLog(logResult, "2", e.toString() != null ? e.toString() : "", "司库");
			return result;
		}

		return result;

	}
}