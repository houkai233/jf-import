package jf.com.shareMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 导入类型枚举
 */
public enum ImportName {
    UPLOAD("上传进度"),
    CHECK("校验进度"),
    RECORD("错误记录"),
    EXCEPTION("异常信息"),
    STATUS("导入状态"),
    FILENAME("文件名称"),
    LASTTIME("导入最后更新时间"),
    TERMINATE("中止标识");

    String remarks;
    ImportName(String remarks){
        this.remarks=remarks;
    }

    public static List<ImportName> getImportNameList(){
        return new ArrayList<>(Arrays.asList(ImportName.values()));
    }
}
