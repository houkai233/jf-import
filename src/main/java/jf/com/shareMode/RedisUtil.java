package jf.com.shareMode;


import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import jf.com.fileReadHandler.FileNamePermit;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.io.FilenameUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.ObjectUtils;

import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.*;

public class RedisUtil<T> {
    //错误记录最大存储条数
    @Setter
    @Getter
    private int errorMaxSize = 500;
    @Setter
    private final Integer batchNum = 40000;
    //数字格式化
    private NumberFormat format = NumberFormat.getNumberInstance();

    //错误记录
    private String recordName = "check_result";
    //上传进度
    private String uploadName = "upload_process";
    //校验进度
    private String checkName = "check_process";
    //异常信息
    private String exceptionName = "exception_info";
    //导入状态 0未导入 1导入中 2.导入完成
    private String statusName = "import_status";
    //导入文件名
    private String importfileName = "import_fileName";
    //导入批次
    private String batchNumber = "batch_number";
    //上次更新时间
    private String lastUpdateTime = "last_updateTime";
    //中止标识 true 终止
    private String terminateFlag = "terminate_flag";

    private RedisTemplate<String, Object> redisTemplate;

    public RedisUtil(RedisTemplate<String, Object> redisTemplate){
        this.redisTemplate=redisTemplate;
    }

    /**
     * 存中止标识
     * @param keyName
     * @param flag
     */
    public void putTerminateFlag(String keyName,boolean flag){
        redisTemplate.opsForValue().set(terminateFlag + ":" +keyName,flag);
    }

    /**
     * 获取中止标识
     * @param keyName
     * @return
     */
    public boolean getTerminateFlag(String keyName){
        Object obj = redisTemplate.opsForValue().get(terminateFlag + ":" + keyName);
        if(ObjectUtils.isEmpty(obj)){
            return false;
        }else {
            return (boolean)obj;
        }
    }

    /**
     * 存导入批次数
     * @param keyName
     * @param batchNum
     */
    public void putBatchNumber(String keyName, Integer batchNum) {
        redisTemplate.opsForValue().set(batchNumber + ":" +keyName,batchNum);
    }

    /**
     * 获取导入的批次数
     * @param keyName
     * @return
     */
    public Integer getBatchNumber(String keyName ) {
        Object obj = redisTemplate.opsForValue().get(batchNumber + ":" + keyName);
        return obj==null?0:(Integer)obj;
    }

    /**
     * 加入错误记录
     *
     */
    public void putRecord(String keyName, List<T> objList) {
        if(ObjectUtil.isEmpty(objList)) return;
        if (objList.size() >= errorMaxSize) {
            objList = objList.subList(0, errorMaxSize);
        }
        redisTemplate.opsForValue().set(recordName + ":" + keyName, JSON.toJSONString(objList,SerializerFeature.WriteMapNullValue));

    }

    /**
     * 获取错误记录
     *
     */
    public List<T> getRecord(String keyName,Class<T> clazz) {
        //获取key对应value
        Object obj = redisTemplate.opsForValue().get(recordName + ":" + keyName);

        if (obj != null) {
            return JSONObject.parseArray((String) obj, clazz);
        } else {
            return new ArrayList<>();
        }
    }

    /**
     * 加入上传进度
     *
     */
    public void putUploadProcess(String keyName,String process) {
        //设置上传进度
        redisTemplate.opsForValue().set(uploadName + ":" + keyName,process);
    }

    /**
     * 获取上传进度
     *
     */
    public String getUploadProcess(String keyName) {
        //获取上传进度
        Object obj = redisTemplate.opsForValue().get(uploadName + ":" + keyName);

        return obj == null ? "0" : (String) obj;
    }


    /**
     * 加入校验进度
     *
     */
    public void putCheckProcess(String keyName,Integer currentSize, Integer totalSize) {
        Object obj = redisTemplate.opsForValue().get(checkName + ":" +keyName);

        if (obj != null) {
            Map<String, Integer> map = (Map<String, Integer>) obj;
            Integer oldcurrentSize = map.get(CheckName.currentSize.toString());
            map.put(CheckName.currentSize.toString(), oldcurrentSize + currentSize);
            redisTemplate.opsForValue().set(checkName + ":" + keyName, map);
        } else {
            Map<String, Integer> map = new HashMap<>();
            map.put(CheckName.currentSize.toString(), currentSize);
            map.put(CheckName.totalSize.toString(), totalSize);
            redisTemplate.opsForValue().set(checkName + ":" + keyName, map);
        }

    }


    /**
     * 获取校验进度
     *
     */
    public String getCheckProcess(String keyName) {
        //获取key对应value
        Object obj = redisTemplate.opsForValue().get(checkName + ":" +keyName);
        format.setRoundingMode(RoundingMode.DOWN);
        format.setMaximumFractionDigits(2);
        if (obj != null) {
            Map<String, Integer> map = (Map<String, Integer>) obj;
            Double currentSize = Double.valueOf(map.get(CheckName.currentSize.toString()));
            Double totalSize = Double.valueOf(map.get(CheckName.totalSize.toString()));
            if (totalSize == 0) {
                putImportStatus(keyName, 2);
                return "1";
            }
            String process = format.format(currentSize / totalSize);
            if ("1".equals(process)) {
                putImportStatus(keyName,2);
            }
            return process;
        } else {
            return "0";
        }
    }

    /**
     * 获取导入记录数
     *
     */
    public Integer getTotalSize(String keyName) {
        //获取key对应value
        Object obj = redisTemplate.opsForValue().get(checkName + ":" + keyName);

        if (obj != null) {
            Map<String, Integer> map = (Map<String, Integer>) obj;
            return map.get(CheckName.totalSize.toString())-1;
        }else {
            return 0;
        }
    }


    /**
     * 存储导入过程中的异常信息
     *
     */
    public void putExceptionInfo(String keyName,String exceptionInfo) {
        redisTemplate.opsForValue().set(exceptionName + ":" + keyName, exceptionInfo);
        //报错设置导入状态为已完成
        putImportStatus(keyName,2);

    }

    /**
     * 获取导入过程中的异常信息
     *
     */
    public String getExceptionInfo(String keyName) {
        Object exceptionInfo = redisTemplate.opsForValue().get(exceptionName + ":" +keyName);

        return exceptionInfo==null?"":(String) exceptionInfo;
    }

    /**
     * 存储当前用户文件导入状态  0.未导入 1.导入中 2.导入完成
     *
     */
    public void putImportStatus(String keyName,Integer status) {
        Object obj = redisTemplate.opsForValue().get(statusName + ":" + keyName);
        if(obj!=null){
            if((Integer)obj==1&&status==2){
                putLastTime(keyName);
            }
        }
        redisTemplate.opsForValue().set(statusName + ":" +keyName, status);
    }

    /**
     * 存储最后更新时间
     * @param keyName
     */
    public void putLastTime(String keyName) {
        redisTemplate.opsForValue().set(lastUpdateTime + ":" + keyName,new Date());
    }

    /**
     * 获取最后更新时间
     * @param keyName
     * @return
     */
    public Date getLastTime(String keyName) {
        Object obj = redisTemplate.opsForValue().get(lastUpdateTime + ":" + keyName);
        if(obj!=null){
            return (Date)obj;
        }
        return null;
    }

    /**
     * 获取当前用户文件导入状态
     *
     */
    public Integer getImportStatus(String keyName) {

        Object obj = redisTemplate.opsForValue().get(statusName + ":" +keyName);

        if (obj != null) {
            Integer status = (Integer) obj;
            if (status != 1) {
                //删除导入文件名
                redisTemplate.delete(importfileName + ":" +keyName);
            }
            return status;
        } else {
            return 0;
        }
    }


    /**
     * 加入导入时的文件名
     *
     */
    public void putFileName(String keyName,String fileName) {

        redisTemplate.opsForValue().set(importfileName + ":" + keyName, fileName);

    }

    /**
     * 获取导入时的文件名
     *
     */
    public String getFileName(String keyName) {

        Object obj = redisTemplate.opsForValue().get(importfileName + ":" +keyName);

        return obj==null?"":(String) obj;
    }

    /**
     * 判断是否校验完成
     *
     * @param keyName
     * @return
     */
    public boolean isCheckProcessComplete(String keyName) {
        //获取key对应value
        Object fileName = redisTemplate.opsForValue().get(importfileName + ":" + keyName);
        if(fileName!=null){
            if(ArrayUtil.containsIgnoreCase(FileNamePermit.excelPermit, FilenameUtils.getExtension((String) fileName))){
                //文件是excel的直接返回true,因为excel只有一批次
                return true;
            }
        }

        Object checkObj = redisTemplate.opsForValue().get(checkName + ":" + keyName);
        Object batchObj = redisTemplate.opsForValue().get(batchNumber + ":" + keyName);
        if (checkObj != null&&batchObj!=null) {
            Map<String, Integer> map = (Map<String, Integer>) checkObj;
            Integer totalSize = map.get("totalSize");
            //总数除以每批次数量为总批次数
            int num=(int) Math.ceil((double)totalSize/(double)batchNum);
            Integer batchNumber=(Integer)batchObj;
            //当前存入批次等于总批次数则说明是最后一批次
            if(num==batchNumber){
                return true;
            }
        }
        return false;
    }

    /**
     * 清除redis导入相关数据
     *
     */
    public void clearRedis(String keyName,List<ImportName> removeList) {
        for(ImportName param:removeList){
            if(ImportName.UPLOAD.equals(param)){
                //删除上传进度
                redisTemplate.delete(uploadName + ":" +keyName);
            }else if(ImportName.CHECK.equals(param)){
                //删除校验进度
                redisTemplate.delete(checkName + ":" + keyName);
            }else if(ImportName.RECORD.equals(param)){
                //删除错误记录
                redisTemplate.delete(recordName + ":" +keyName);
            }else if(ImportName.EXCEPTION.equals(param)){
                //删除报错信息
                redisTemplate.delete(exceptionName + ":" + keyName);
            }else if(ImportName.STATUS.equals(param)){
                //删除导入状态
                redisTemplate.delete(statusName + ":" + keyName);
            }else if(ImportName.FILENAME.equals(param)){
                //删除导入文件名
                redisTemplate.delete( importfileName+":"+keyName);
            }else if(ImportName.LASTTIME.equals(param)){
                //删除上次更新时间
                redisTemplate.delete( lastUpdateTime+":"+keyName);
            }else if(ImportName.TERMINATE.equals(param)){
                //删除上次更新时间
                redisTemplate.delete( terminateFlag+":"+keyName);
            }
        }

    }

    public void cleanRedisByName(String keyName,ImportName... importNames){
        List<ImportName> removeList=new ArrayList<>();
        for(ImportName data:importNames){
            removeList.add(data);
        }
        clearRedis(keyName,removeList);
    }

    /**
     * 根据名称获取导入中存入redis的数据
     * @param keyName
     * @param selectList
     * @return
     */
    public ImportData getImportRedisData(String keyName,List<ImportName> selectList){
        ImportData data=new ImportData();
        for(ImportName params:selectList){
            if(ImportName.LASTTIME.equals(params)){
                data.setLastTime(getLastTime(keyName));
            }else if(ImportName.UPLOAD.equals(params)){
                data.setUploadProcess(getUploadProcess(keyName));
            }else if(ImportName.CHECK.equals(params)){
                data.setCheckProcess(getCheckProcess(keyName));
            }else if(ImportName.EXCEPTION.equals(params)){
                data.setExceptionInfo(getExceptionInfo(keyName));
            }else if(ImportName.STATUS.equals(params)){
                data.setImportStatus(getImportStatus(keyName));
            }else if(ImportName.FILENAME.equals(params)){
                data.setFileName(getFileName(keyName));
            }
        }
        return data;
    }

    public ImportData getImportRedisData(String keyName,ImportName... importNames){
        List<ImportName> selectList=new ArrayList<>();
        for(ImportName data:importNames){
            selectList.add(data);
        }
       return this.getImportRedisData(keyName,selectList);
    }

}
