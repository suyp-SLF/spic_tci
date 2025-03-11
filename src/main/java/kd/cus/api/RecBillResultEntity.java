package kd.cus.api;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 司库集成收款单web api返回结构
 *
 * @author Wu Yanqi
 */
public class RecBillResultEntity implements Serializable {
    private static final long serialVersionUID = 7179157977886036267L;
    private Boolean success;
    private String billno;
    private String message;

    public RecBillResultEntity(Boolean success, String billno, String message) {
        this.success = success;
        this.billno = billno;
        this.message = message;
    }

    public static RecBillResultEntity PROCESS_SUCCESS(String billno) {
        return new RecBillResultEntity(true, billno, "success");
    }

    public static RecBillResultEntity PROCESS_ERROR(String billno, String message) {
        return new RecBillResultEntity(false, billno, message);
    }

    public static Map<String, Object> toMap(RecBillResultEntity item) {
        Map<String, Object> ret = new HashMap();
        ret.put("success", item.getSuccess());
        ret.put("billno", item.getBillno());
        ret.put("message", item.getMessage());
        return ret;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getBillno() {
        return billno;
    }

    public void setBillno(String billno) {
        this.billno = billno;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
