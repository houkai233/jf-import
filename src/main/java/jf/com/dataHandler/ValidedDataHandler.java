package jf.com.dataHandler;


import jf.com.fileReadHandler.DevException;
import jf.com.shareMode.RedisUtil;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections4.ListUtils;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

public class ValidedDataHandler<T extends ValidedData, P> {
    @Getter
    @Setter
    private int validNum = 1000;

    private String token;

    private DataValider<T,P> dataValider;
    private RedisUtil<T> redisUtil;


    /**
     * @param token
     * @param redisUtil
     * @param dataValider  需自定义实现
     */
    public ValidedDataHandler(@NotNull String token, @NotNull RedisUtil<T> redisUtil, @NotNull DataValider<T, P> dataValider) {
        this.token = token;
        this.redisUtil = redisUtil;
        this.dataValider = dataValider;

    }

    /**
     * 每批次先存临时表，再来校验
     * 最后一批次来校验时，需要保证relationValider.validRelation的数据完整
     *
     * @param dataList
     * @param totalSize 若非临时表处理，等于dataList.size()
     */
    public void batchValidData(List<T> dataList, Class<T> cla,int totalSize, P transferParams) {
        //获取错误记录
        List<T> record = redisUtil.getRecord(token,cla);
        //对数据分批次 用于分批存储校验进度
        List<List<T>> partition = ListUtils.partition(dataList, validNum);
        List<T> errorData = new ArrayList<>(record);
        for (List<T> list : partition) {
            //获取中止标识
            if(redisUtil.getTerminateFlag(token)) {
                throw new DevException("导入中止");
            }
            //错误记录超过500条就不进行校验
            if (errorData.size() < redisUtil.getErrorMaxSize()) {
                errorData.addAll(dataValider.commonVaild(dataList, transferParams));
            }
            //存校验进度
            redisUtil.putCheckProcess(token, list.size(), totalSize + 1);
        }

        //存错误记录
        redisUtil.putRecord(token,errorData);
    }

}
