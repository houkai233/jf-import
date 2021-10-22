package jf.com.shareMode;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class ImportData {
 /*   *//**
     * 错误记录
     *//*
    private List<JSONObject> errorDataList;*/
    /**
     * 上传进度
     */
    private String uploadProcess;
    /**
     * 校验进度
     */
    private String checkProcess;
    /**
     * 异常信息
     */
    private String exceptionInfo;
    /**
     * 导入状态 0未导入 1导入中 2.导入完成
     */
    private Integer importStatus;
    /**
     * 导入文件名
     */
    private String fileName;
    /**
     * 上次更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date lastTime;
}
