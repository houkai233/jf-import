package jf.com.dataHandler;

import cn.hutool.core.util.StrUtil;
import jf.com.shareMode.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.validation.ConstraintViolationException;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

@Component
public abstract class DataValider<T extends ValidedData, P> {
    @Autowired
    AnnotationDataValider<T> dataValider;

    /**
     * 校验数据 校验进度,错误记录存入redis
     *
     * @param cla
     * @param dataValider
     * @param transparams
     * @param voList
     * @param token
     * @param redisUtil
     * @return
     */
    public boolean validDataIsError(Class<T> cla, DataValider<T, P> dataValider, P transparams, List<T> voList, int totalSize, String token, RedisUtil redisUtil) {

        //校验数据
        ValidedDataHandler<T, P> handler = new ValidedDataHandler<T, P>(token, redisUtil, dataValider);
        handler.batchValidData(voList, cla, totalSize, transparams);

        if (voList.size() == totalSize) {
            List<T> record = redisUtil.getRecord(token, cla);
            if (record.size() > 0) {
                //一批次数据，有错误记录则直接返回
                redisUtil.putCheckProcess(token, 1, totalSize);
                return true;
            }
        }

        return false;
    }

    /**
     * 校验数据 可实现该方法实现自定义校验
     *
     * @param models
     * @param transferParams
     * @return
     */
    public List<T> commonVaild(List<T> models, P transferParams) {
        List<T> resultList = new ArrayList<>();
        for (T model : models) {
            String commonErrorStr = dataValid(model);
            if (StrUtil.isNotBlank(commonErrorStr)) {
                String wMsg = model.getErrorHint();
                model.setErrorHint(null);
                model.appendMsg(commonErrorStr);
                model.appendMsg(wMsg);
            }
            if (model.getErrorHint() != null) {
                resultList.add(model);
            }
        }
        return resultList;
    }

    public String dataValid(@NotNull T model) {

        try {
            //注解校验
            dataValider.dataValid(model);
            return null;
        } catch (ConstraintViolationException e) {
            //报错说明当前数据不符合注解校验，取结果
            return e.getMessage().replace("dataValid.model.", "");
        }
    }

    /**
     * 读取文件后的回调方法
     *
     * @param token       存redis名称
     * @param list        批次数据
     * @param totalSize   总的记录数
     * @param transparams 自定义的参数
     */
    public abstract void batchCallBack(String token, List<T> list, int totalSize, P transparams);


}
