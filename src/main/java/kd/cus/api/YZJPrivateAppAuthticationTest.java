package kd.cus.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import kd.bos.dc.api.model.Account;
import kd.bos.exception.KDException;
import kd.bos.exception.LoginErrorCode;
import kd.bos.lang.Lang;
import kd.bos.logging.Log;
import kd.bos.logging.LogFactory;
import kd.bos.login.lang.LoginLangUtils;
import kd.bos.login.thirdauth.app.AppAuthResult;
import kd.bos.login.thirdauth.app.ThirdAppAuthtication;
import kd.bos.login.thirdauth.app.UserType;
import kd.bos.login.thirdauth.app.yzj.YZJPrivateAppAuthtication;
import kd.bos.login.utils.HttpUtils;
import kd.bos.login.utils.LoginUtils;
//import kd.bos.login.utils.SignUtils;
import kd.bos.login.utils.StringUtils;
import kd.bos.login.yzjprivate.lightapp.AppContext;
import kd.bos.login.yzjprivate.lightapp.YunzhijiaPcdTicketService;
import kd.bos.util.ExceptionUtils;
import kd.bos.util.JSONUtils;
import kd.bos.util.resource.Resources;

public class YZJPrivateAppAuthticationTest extends ThirdAppAuthtication {
	private static Log logger = LogFactory.getLog(YZJPrivateAppAuthtication.class);

	public boolean isNeedHandle(HttpServletRequest request, Account currentCenter) {
		boolean isNeed = false;
		logger.info(String.format("%s's isNeedHandle is invoked.", new Object[]{"YZJPrivateAppAuthtication"}));
		String ticket = request.getParameter("ticket");
		logger.info("获取ticket:"+ticket);
		String path = request.getRequestURI();
		logger.info("获取path:"+path);
		String appId = LoginUtils.getAPPId(request);
		logger.info("获取appId:"+appId);
		String token = request.getParameter("token");
		logger.info("获取token:"+token);
		if (((path.toLowerCase().contains("/mobile.html")) || (path.contains("/qing/lappEntrance.do"))
				|| (StringUtils.isNotEmpty(appId))) && (!StringUtils.isEmpty(ticket))) {
			logger.info("进入path.toLowerCase().contains(\"/mobile.html\"))if中:---------------------");
			isNeed = true;
		}
		if ((!isNeed) && ("post".equalsIgnoreCase(request.getMethod()))) {
			logger.info("进入(!isNeed)if中:---------------------");
			List<String> pathList = getConfiguredPaths();
			if (pathList == null) {
				pathList = new ArrayList();
				pathList.add(getDefaultUrl());
			}
			for (String newPath : pathList) {
				if (path.contains(newPath)) {
					isNeed = true;
					logger.info("进入path.contains(newPath)中:---------------------");
					break;
				}
			}
		}
		logger.info("isNeed=" + isNeed);
		return false;
	}

	public AppAuthResult appAuthtication(HttpServletRequest request, Account currentCenter) {
		logger.info("进入appAuthticatio方法中:---------------------");
		AppAuthResult result = new AppAuthResult();
		logger.info("获取result:"+result);
		result.setSucceed(false);
		logger.info(String.format("%s's appAuthtication is invoked.", new Object[]{"YZJPrivateAppAuthtication"}));
		try {
			YZJResult yzjResult = getYZJResult(request);
			logger.info("获取yzjResult:"+yzjResult);
			if (yzjResult != null) {
				logger.info(String.format("yzjResult=%s", new Object[]{yzjResult.toString()}));
				logger.info("进入yzjResultif中:---------------------");
				String newSign = generatSingkey(yzjResult, currentCenter.getTenantId());
				if ((StringUtils.isNotEmpty(newSign)) && (newSign.equals(yzjResult.getSign()))) {
					logger.info("进入(StringUtils.isNotEmpty(newSign)) 方法中:---------------------");
					result.setSucceed(true);
					result.setUserFlag(yzjResult.getOpenId());
					result.setUserType(UserType.OPEN_ID);
				}
			} else {
				logger.info("yzjResult is null");
			}
			if (!result.isSucceed()) {
				logger.info("进入!result.isSucceed()中:---------------------");
				String ticket = request.getParameter("ticket");
				logger.info("获取ticket:"+ticket);
				String path = request.getServletPath();
				logger.info("获取path:"+path);
				String appId = LoginUtils.getAPPId(request);
				logger.info("获取appId:"+appId);
				Lang lang = LoginLangUtils.getLoginLanguage(request);
				logger.info("获取lang:"+lang);
				AppContext appCtx = YunzhijiaPcdTicketService.getContextByTicket(currentCenter.getTenantId(), appId,
						ticket);
				logger.info("获取appCtx:"+appCtx);
				if (appCtx != null) {
					logger.info("进入appCtx != null:---------------------");
					result.setSucceed(true);

					result.setUserFlag(appCtx.getMobile());
					result.setUserType(UserType.MOBILE_PHONE);
				}
			}
		} catch (KDException ex) {
			throw ex;
		} catch (Exception e) {
			logger.error(e);
			String sMsg = String.format(Resources.getString("App验证错误:1)租户尚未开通云ERP。2)%s", "YZJPrivateAppAuthtication_0",
					"bos-login", new Object[0]), new Object[]{ExceptionUtils.getExceptionStackTraceMessage(e)});
			throw new KDException(LoginErrorCode.loginBizException, new Object[]{sMsg});
		}
		logger.info("last resut=" + result.isSucceed());
		String loginType = request.getParameter("logintype");
//		if ("web".equalsIgnoreCase(loginType)) {
//			((Object) result).setWeb(true);
//		}
		return result;
	}

	private String generatSingkey(YZJResult yzjResult, String tenantId) {
		String newKey = null;
		if (yzjResult != null) {
			logger.info("yzjResult != null:---------------------");
			String signKey = getSignKey(tenantId);
			logger.info("generatSingkey" + signKey);
			if (StringUtils.isNotEmpty(signKey)) {
				try {
//					newKey = SignUtils.genSign(signKey, yzjResult.getOpenId(), yzjResult.getSourceType(),
//							yzjResult.getIdList());
				} catch (Exception e) {
					logger.error(e);
				}
			} else {
				logger.error("不能获得app sign key value ");
			}
		}
		return newKey;
	}

	private String getSignKey(String tenantId) {
		String signKey = System.getProperty(tenantId + "_yzj_app_sign_key");
		if (StringUtils.isEmpty(signKey)) {
			signKey = System.getProperty("yzj_app_sign_key");
		}
		return signKey;
	}

	private YZJResult getYZJResult(HttpServletRequest request) {
		YZJResult result = null;
		if (!"post".equalsIgnoreCase(request.getMethod())) {
			return null;
		}
		String body =null;
		//String body = HttpUtils.getBody(request);
		if (StringUtils.isNotEmpty(body)) {
//			try {
//				logger.info("body=" + body);
//
////				Map<String, Object> bodyMap = (Map) JSONUtils.cast(body, HashMap.class, true);
////				if (bodyMap != null) {
////					result = new YZJResult();
////				//	result.setSign(StringUtils.getStringValue(bodyMap.get("sign")));
////					if (bodyMap.get("param") != null) {
////						Map<String, Object> paramMap = (Map) bodyMap.get("param");
////						result.setOpenId(StringUtils.getStringValue(paramMap.get("openId")));
////						result.setSourceType(StringUtils.getStringValue(paramMap.get("sourceType")));
////						if (paramMap.get("sourceIds") != null) {
////							List<String> idList = (List) paramMap.get("sourceIds");
////							result.setIdList(idList);
////						}
////					}
////				}
//			} catch (IOException e) {
//				logger.error(e);
//			}
		}
		return result;
	}

	private List<String> getConfiguredPaths() {
		List<String> list = null;
		String cString = System.getProperty("yzj_sign_urls");
		if (StringUtils.isNotEmpty(cString)) {
			String[] cArray = cString.split(",");
			list = new ArrayList(cArray.length);
			for (String url : cArray) {
				list.add(url.trim());
			}
		}
		return list;
	}

	private String getDefaultUrl() {
		return "/wf/batchAgreeTask";
	}

	static class YZJResult {
		String sign;
		String openId;
		String sourceType;
		List<String> idList;

		public String getSourceType() {
			return sourceType;
		}

		public void setSourceType(String sourceType) {
			this.sourceType = sourceType;
		}

		public String getSign() {
			return sign;
		}

		public void setSign(String sign) {
			this.sign = sign;
		}

		public String getOpenId() {
			return openId;
		}

		public void setOpenId(String openId) {
			this.openId = openId;
		}

		public List<String> getIdList() {
			return idList;
		}

		public void setIdList(List<String> idList) {
			this.idList = idList;
		}

		public String toString() {
			return String.format("sign=%s,openId=%s,sourceType=%s", new Object[]{sign, openId, sourceType});
		}
	}
}