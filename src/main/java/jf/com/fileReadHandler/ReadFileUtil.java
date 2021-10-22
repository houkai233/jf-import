package jf.com.fileReadHandler;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelReader;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.exception.ExcelAnalysisException;
import com.alibaba.excel.exception.ExcelAnalysisStopException;
import com.alibaba.excel.read.metadata.ReadSheet;
import com.linuxense.javadbf.DBFReader;
import jf.com.dataHandler.DataValider;
import jf.com.dataHandler.ValidedData;
import jf.com.shareMode.RedisUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;

import javax.validation.constraints.NotNull;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
public class ReadFileUtil<T extends ValidedData> {
    private RedisUtil<T> redisUtil;
    //dbf文件编码
    @Setter
    private String dbfEncode="GBK";
    //读取dbf文件时每批次数量
    @Setter
    private int dbfBatchNum=40000;
    //读取指定的excel sheet
    @Setter
    private int excelSheetNo=0;
    //读取excel文件时从第几行开始读取
    @Setter
    private int excelRow =1;
    //存储redis的key
    @Getter
    private String token;
    //zip包解压的目录，不设置默认zip包所在目录
    @Setter
    @Getter
    private String zipDir;

    public ReadFileUtil(String token, RedisUtil redisUtil) {
        this.token = token;
        this.redisUtil=redisUtil;
    }
    /**
     * 根据上传文件类型判断调用不同方法读取
     * @param filePath
     * @param cla   读取数据的类型
     * @param dataValider 继承DataValider的实现类
     * @param transparams    需传递的参数，可为空
     */
    public <P> void readFile(@NotNull String filePath, @NotNull Class<T> cla, @NotNull DataValider<T, P> dataValider, P transparams) {
        try {
            File file = new File(filePath);
            // 判断源文件是否存在
            if (!file.exists()) {
                throw new RuntimeException(file.getPath() + "所指文件不存在");
            }
            String extension = FilenameUtils.getExtension(file.getName());
            if (ArrayUtil.containsIgnoreCase(FileNamePermit.zipPermit , extension)) {
                //解压zip包 返回解压后文件路径
                filePath= unZipFile(filePath);
                readFile(filePath,cla,dataValider,transparams);
                return;
            }

            if (ArrayUtil.containsIgnoreCase(FileNamePermit.excelPermit , extension)) {
                //读取excel
                readExcel(filePath, cla, dataValider,transparams);
            } else if (ArrayUtil.containsIgnoreCase(FileNamePermit.dbfPermit , extension)) {
                //读取dbf
                readDBF(filePath, cla, dataValider, transparams);
            } else{
                throw new RuntimeException("文件格式不正确");
            }

        } catch (Exception e) {
            deleteFile(filePath);
            log.error("读取文件出错:",e);
            redisUtil.putExceptionInfo(token, "读取文件出错");
        }
    }

    /**
     * 解压zip包
     */
    private <P> String unZipFile(String filePath) throws IOException {
        File srcFile = new File(filePath);
        // 判断源文件是否存在
        if (!srcFile.exists()) {
            throw new RuntimeException(srcFile.getPath() + "所指文件不存在");
        }
        // 开始解压
        ZipInputStream zis=null;
        String resultPath=null;
        try {
            String destDirPath = srcFile.getParentFile().getPath();
            if (StrUtil.isEmpty(zipDir)){
                zipDir=destDirPath;
            }else {
                destDirPath=zipDir;
            }
            zis = new ZipInputStream ( new FileInputStream (srcFile), Charset.forName("gbk")) ;
            ZipEntry entry = null ;
            while ( ( entry = zis.getNextEntry ( ) ) != null) {

                if (entry.isDirectory()) {
                    redisUtil.putExceptionInfo(token, "上传文件解压失败,zip文件中不能包含文件夹！");
                    return "";
                } else {
                    // 如果是文件，就先创建一个文件，然后用io流把内容copy过去
                    File targetFile = new File(destDirPath + File.separator + UUID.randomUUID().toString().replaceAll("-", "") + "." + FilenameUtils.getExtension(entry.getName()));
                    // 保证这个文件的父文件夹必须要存在
                    if (!targetFile.getParentFile().exists()) {
                        targetFile.getParentFile().mkdirs();
                    }
                    targetFile.createNewFile();
                    // 将压缩文件内容写入到这个文件中
                    FileOutputStream fos = new FileOutputStream(targetFile);
                    int len;
                    byte[] buf = new byte[2 * 1024];
                    while ((len = zis.read(buf)) != -1) {
                        fos.write(buf, 0, len);
                    }
                    // 关流顺序，先打开的后关闭
                    fos.close();
                    zis.closeEntry();
                    resultPath=targetFile.getPath();
                    //读到文件就不再循环
                    break;
                }
            }
            return resultPath;
        } catch (Exception e) {
            log.error("ZIP文件解压失败:",e);
            redisUtil.putExceptionInfo(token, "ZIP文件解压失败");
            return "";
        } finally {
            if(zis !=null)  zis.close();
            deleteFile(filePath);
        }
    }

    /**
     * 读取excel文件
     * 先校验数据，有错误直接设置校验完成返回不继续下去，无错误回调方法，最后设置校验完成
     */
    private <P> void readExcel(String filePath, Class<T> cla, DataValider<T,P> dataValider, P transparams) {
        InputStream inputStream;
        ExcelReader excelReader = null;
        List<T> voList;
        try {
            //读取数据
            inputStream = new FileInputStream(filePath);
            ReadSheet readSheet = EasyExcel.readSheet(excelSheetNo).build();
            ExcelListener<T> listener = new ExcelListener<T>();
            excelReader = EasyExcel.read(inputStream, cla, listener).registerConverter(new CustomStringNumberConverter()).headRowNumber(excelRow).build();
            excelReader.read(readSheet);
            //获取导入数据
            voList = listener.getVoList();
            if(voList.size()==0){
                redisUtil.putExceptionInfo(token,"导入文件无数据");
                return;
            }
            //校验数据
            if (dataValider.validDataIsError(cla, dataValider, transparams, voList,voList.size(),token,redisUtil)) return;

            //添加批次数，excel只有一批次
            redisUtil.putBatchNumber(token,1);
            //调用回调方法
            dataValider.batchCallBack(token,voList, voList.size(),transparams);
            //设置校验完成
            redisUtil.putCheckProcess(token,1,voList.size());
        } catch (DevException e) {
            log.error("自定义异常抛出:{}",e.getMsg());
            redisUtil.putExceptionInfo(token, e.getMsg());
        } catch (Exception e) {
            log.error("读取EXCEL失败:",e);
            redisUtil.putExceptionInfo(token, "读取EXCEL失败");
        } finally {
            // 这里千万别忘记关闭，读的时候会创建临时文件，不然磁盘会崩的
            if (excelReader != null) {
                excelReader.finish();
            }
            deleteFile(filePath);
        }

    }


    /**
     * 校验文件模板 限excel pdf
     * @param filePath
     * @param cla
     * @return
     */
    public String checkFileTemplate(String filePath, Class<T> cla){
        try {
            File file = new File(filePath);
            // 判断源文件是否存在
            if (!file.exists()) {
                throw new RuntimeException(file.getPath() + "所指文件不存在");
            }

            String extension = FilenameUtils.getExtension(file.getName());
            if (ArrayUtil.containsIgnoreCase(FileNamePermit.zipPermit , extension)) {
                //解压zip包 返回解压后文件路径
                filePath= unZipFile(filePath);
                return checkFileTemplate(filePath,cla);
            }
            if (ArrayUtil.containsIgnoreCase(FileNamePermit.excelPermit , extension)) {
                //校验excel
                return checkExcelTemplate(filePath, cla);
            } else if (ArrayUtil.containsIgnoreCase(FileNamePermit.dbfPermit , extension)) {
                //校验dbf
                return checkDbfTemplate(filePath, cla);
            }else {
                throw new RuntimeException("文件格式不正确");
            }
        }catch (Exception e){
            log.error("模板校验报错:",e);
            redisUtil.putExceptionInfo(token, "模板校验报错");
            return filePath;
        }
    }

    /**
     * 校验excel文件模板 true模板符合 false模板不符合
     * @param filePath
     * @param cla
     * @return
     * @throws FileNotFoundException
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    private String checkExcelTemplate(String filePath, Class<T> cla) throws FileNotFoundException, IllegalAccessException, InstantiationException {
        InputStream inputStream = new FileInputStream(filePath);
        //校验模板
        List<Map<Integer, String>> validMap = new ArrayList<>();
        try {
            // 读取第一个sheet 文件流会自动关闭
            EasyExcel.read(inputStream, new AnalysisEventListener<Map<Integer, String>>() {
                //直接使用Map来保存数据
                @Override
                public void invoke(Map<Integer, String> rowData, AnalysisContext context) {
                    //读取到的每行数据,其key是以0开始的索引
                    //读取完表头就退出不再读取
                    if (validMap.size() == 1) throw new ExcelAnalysisStopException();
                    validMap.add(rowData);

                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                    //所有行都解析完成
                }
            }).sheet(excelSheetNo).headRowNumber(excelRow-1).doRead();
        } catch (ExcelAnalysisException e) {

        }
        //没有数据直接结束
        if(validMap.size()==0){
            redisUtil.putExceptionInfo(token, "没有读取到模板表头");
            return filePath;
        }

        //校验表头，判断模板是否为当前导入类型
        boolean flag = checkTemplate(validMap.get(0), cla);
        return filePath;
    }

    /**
     * 校验dbf文件模板 true模板符合 false模板不符合
     * @param filePath
     * @param cla
     * @return
     * @throws FileNotFoundException
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    private String checkDbfTemplate(String filePath, Class<T> cla) throws FileNotFoundException, IllegalAccessException, InstantiationException {
        Charset charset = Charset.forName(dbfEncode);
        InputStream fileInputStream = new FileInputStream(filePath);
        DBFReader dbfReader = new DBFReader(fileInputStream, charset);
        //获取表头
        Map<Integer, String> map = new HashMap();
        for (int i = 0; i < dbfReader.getFieldCount(); i++) {
            map.put(i, dbfReader.getField(i).getName());
        }
        dbfReader.close();
        if(map.size()==0){
            redisUtil.putExceptionInfo(token, "没有读取到表头");
            return filePath;
        }
        //校验模板
        boolean flag = checkTemplate(map, cla);
        return filePath;
    }

    /**
     * 模板校验
     * 文件列表名与对应类属性名比较，有文件列表名不在类属性名中则模板不符合
     */
    private boolean checkTemplate(Map<Integer, String> map, Class<T> cla) throws IllegalAccessException, InstantiationException {
        //获取类属性名称
        Object obj = cla.newInstance();
        Field[] field = obj.getClass().getDeclaredFields();
        List<String> fieldList = new ArrayList<>();
        for (Field f : field) {
            f.setAccessible(true);
            String name = f.getName().toLowerCase();
            fieldList.add(name);
        }
        //判断表头名称是否在类属性中存储 true模板正确 false模板不正确
        boolean flag = true;
        for (Integer key : map.keySet()) {
            String value = map.get(key);
            if (!fieldList.contains(value.toLowerCase())) {
                //不存在此表头说明模板不正确
                flag = false;
                break;
            }
        }
        if (!flag) {
            redisUtil.putExceptionInfo(token, "导入模板不符合当前模板类型标准");
        }
        return flag;
    }

    /**
     * 读取dbf文件
     * 先读取一次文件进行数据校验，发现有错误记录则直接设置校验完成不继续向下，
     * 校验通过的话再次读取一次文件并回调方法传入读取的数据并在回调完后设置校验完成
     *
     */
    private <P> void readDBF(String filePath, Class<T> cla, DataValider<T, P> dataValider, P transparams) {
        DBFReader dbfReader = null;
        try {
            Charset charset = Charset.forName(dbfEncode);
            InputStream fileInputStream = new FileInputStream(filePath);
            dbfReader = new DBFReader(fileInputStream, charset);
            int recodeNum = dbfReader.getRecordCount();
            if(recodeNum==0){
                redisUtil.putExceptionInfo(token,"导入文件无数据");
                return;
            }

            if(recodeNum<=dbfBatchNum){
              //一批次小数据
                List<T> list =new ArrayList<>();
                if (smallDataValid(cla, dataValider, transparams, dbfReader,list)) return;
                //调用回调方法
                dataValider.batchCallBack(token,list,recodeNum,transparams);
            }else {
                //多批次大数据
                if (bigDataValid(cla, dataValider, transparams, dbfReader,recodeNum)) return;
                //校验没有发现错误则再次读取数据调用回调方法
                dataInsert(cla,dataValider,transparams,filePath,recodeNum);
            }

            //设置校验完成
            redisUtil.putCheckProcess(token,1,recodeNum);
        }catch (DevException e) {
            log.error("自定义异常抛出:{}",e.getMsg());
            redisUtil.putExceptionInfo(token, e.getMsg());
        } catch (Exception e) {
            log.error("读取DBF失败:",e);
            redisUtil.putExceptionInfo(token, "读取DBF失败");
        } finally {
            if (dbfReader != null) {
                dbfReader.close();
            }
            deleteFile(filePath);
        }

    }

    /**
     * 小数据的校验
     * 直接读取所有数据进行校验，有错误记录返回true
     * @param cla
     * @param dataValider
     * @param transparams
     * @param dbfReader
     * @param <P>
     * @return
     * @throws InstantiationException
     * @throws IllegalAccessException
     */
    private <P> boolean smallDataValid(Class<T> cla, DataValider<T, P> dataValider, P transparams, DBFReader dbfReader,List<T> list) throws InstantiationException, IllegalAccessException {
        Object[] rowObjects;
        while ((rowObjects = dbfReader.nextRecord()) != null) {
            T obj = cla.newInstance();
            Field[] field = obj.getClass().getDeclaredFields();
            for (int i = 0; i < rowObjects.length; i++) {
                for (Field f : field) {
                    f.setAccessible(true);
                    if (f.getName().equalsIgnoreCase(dbfReader.getField(i).getName())) {
                        f.set(obj, ObjectUtil.isEmpty(rowObjects[i]) ? "" : String.valueOf(rowObjects[i]).trim());
                        break;
                    }
                }
            }
            list.add(obj);
        }
        redisUtil.putBatchNumber(token,1);
        //数据校验
        return dataValider.validDataIsError(cla, dataValider, transparams, list,list.size(),token,redisUtil);
    }

    /**
     * 大数据的校验
     * 根据批次数据分批次读取数据进行数据校验，等所有校验完查询redis是否有错误记录,有则返回true
     * @param cla
     * @param dataValider
     * @param transparams
     * @param dbfReader
     * @param recodeNum
     * @param <P>
     * @return
     * @throws InstantiationException
     * @throws IllegalAccessException
     */
    private <P> boolean bigDataValid(Class<T> cla, DataValider<T, P> dataValider, P transparams, DBFReader dbfReader, int recodeNum) throws InstantiationException, IllegalAccessException {
        List<T> list = new ArrayList<>();
        int batch=0;
        Object[] rowObjects;
        while ((rowObjects = dbfReader.nextRecord()) != null) {
            T obj = cla.newInstance();
            Field[] field = obj.getClass().getDeclaredFields();
            for (int i = 0; i < rowObjects.length; i++) {
                for (Field f : field) {
                    f.setAccessible(true);
                    if (f.getName().equalsIgnoreCase(dbfReader.getField(i).getName())) {
                        f.set(obj, ObjectUtil.isEmpty(rowObjects[i]) ? "" : String.valueOf(rowObjects[i]).trim());
                        break;
                    }
                }
            }
            list.add(obj);
            if (list.size() >= dbfBatchNum) {
                //每批次的数据校验
                dataValider.validDataIsError(cla, dataValider, transparams, list,recodeNum,token,redisUtil);
                batch++;
                redisUtil.putBatchNumber(token,batch);

                //清空list
                list.clear();
            }
        }
        if(list.size()>0){
            //最后的数据校验
            dataValider.validDataIsError(cla, dataValider, transparams, list,recodeNum,token,redisUtil);
            batch++;
            redisUtil.putBatchNumber(token,batch);

        }

        //检测所有校验完成后是否有错误记录
        List<T> record = redisUtil.getRecord(token, cla);
        if (record.size()>0) {
            //有错误记录则直接返回
            redisUtil.putCheckProcess(token, 1,recodeNum);
            return true;
        }
        return false;
    }

    /**
     *
     * @param cla
     * @param dataValider
     * @param transparams
     * @param filePath
     * @param recodeNum
     * @param <P>
     * @throws FileNotFoundException
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    private <P> void dataInsert(Class<T> cla, DataValider<T, P> dataValider, P transparams, String filePath, int recodeNum) throws FileNotFoundException, IllegalAccessException, InstantiationException {
        DBFReader dbfReader = null;
        try {
            Charset charset = Charset.forName(dbfEncode);
            InputStream fileInputStream = new FileInputStream(filePath);
            dbfReader = new DBFReader(fileInputStream, charset);
           List<T> list = new ArrayList<>();
           int batch=0;
           Object[] rowObjects;
           while ((rowObjects = dbfReader.nextRecord()) != null) {
               T obj = cla.newInstance();
               Field[] field = obj.getClass().getDeclaredFields();
               for (int i = 0; i < rowObjects.length; i++) {
                   for (Field f : field) {
                       f.setAccessible(true);
                       if (f.getName().equalsIgnoreCase(dbfReader.getField(i).getName())) {
                           f.set(obj, ObjectUtil.isEmpty(rowObjects[i]) ? "" : String.valueOf(rowObjects[i]).trim());
                           break;
                       }
                   }
               }
               list.add(obj);
               if (list.size() >= dbfBatchNum) {
                   batch++;
                   redisUtil.putBatchNumber(token,batch);
                   //调用回调方法
                   dataValider.batchCallBack(token,list,recodeNum,transparams);
                   //清空list
                   list.clear();
               }
           }
           if(list.size()>0){
               batch++;
               redisUtil.putBatchNumber(token,batch);
               //调用回调方法
               dataValider.batchCallBack(token,list,recodeNum,transparams);
           }
       }catch (Exception e){
            throw e;
       }finally {
            if(dbfReader!=null) dbfReader.close();
       }

    }

    /**
     * 删除文件
     * @param filePath
     * @return
     */
    public Boolean deleteFile(String filePath) {
        File file = new File(filePath);
        if (file.exists()) {
            return file.delete();
        }
        return true;
    }
}
