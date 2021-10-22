package jf.com.dataHandler;

import cn.hutool.core.util.StrUtil;

import java.io.Serializable;


public interface ValidedData extends Serializable {
    String getErrorHint();

    void setErrorHint(String msg);

    default void appendMsg(String msg) {
        if (StrUtil.isNotBlank(getErrorHint()) && StrUtil.isNotBlank(msg)) {
            setErrorHint(StrUtil.concat(true, getErrorHint(), "; "));
        }
        setErrorHint(StrUtil.concat(true, getErrorHint(), msg));
    }
}
