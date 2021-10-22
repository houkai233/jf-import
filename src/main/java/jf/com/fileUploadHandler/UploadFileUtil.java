package jf.com.fileUploadHandler;


import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import jf.com.shareMode.RedisUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.ProgressListener;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.text.NumberFormat;
import java.util.List;
import java.util.UUID;

@Slf4j
public class UploadFileUtil {

    private RedisUtil redisUtil;
    @Getter
    private String token;
    @Getter
    private HttpServletRequest request;
    @Getter
    private ServletFileUpload upload;
    @Getter
    private String fileName;
    //上传文件所在目录
    @Setter
    private String repository;
    /**
     * 统一的编码格式
     */
    private final String encode = "UTF-8";

    /**
     * progressListener回调给进度
     * @param token
     * @param progressListener
     */
    public UploadFileUtil(String token, RedisUtil redisUtil, ProgressListener progressListener){
        this.token = token;
        this.redisUtil=redisUtil;
        upload = new ServletFileUpload();
        if(ObjectUtil.isEmpty(progressListener)){
            //数字格式化
            NumberFormat format = NumberFormat.getNumberInstance();
            //设置数值的小数部分允许的最大位数
            format.setMaximumFractionDigits(2);
            progressListener = new ProgressListener() {
                Double pro=0d;

                @Override
                public void update(long pBytesRead, long pContentLenght, int i) {
                    if (pContentLenght == -1) {
                        return;
                    }
                    //pBytesRead:已读取到的文件大小
                    //pContentLenght：文件大小
                    double lpBytesRead = pBytesRead;
                    double lpContentLenght = pContentLenght;

                    String process = format.format(lpBytesRead / lpContentLenght);
                    //如果当前进度比上一次大1，就存入redis
                    if (Double.parseDouble(process) - pro > 0.01) {
                        //存上传进度
                        redisUtil.putUploadProcess(token, process);
                    }
                    pro = Double.valueOf(process);
                }
            };
        }
        upload.setProgressListener(progressListener);
        //处理乱码问题
        upload.setHeaderEncoding(encode);
        //设置单个文件的最大值
//        upload.setFileSizeMax(1024 * 1024 * 10);
        //设置总共能够上传文件的大小
//        upload.setSizeMax(1024 * 1024 * 10);
    }


    public UploadFileUtil(String token, RedisUtil redisUtil) {
        this(token, redisUtil,null);
    }


    /**
     * 上传文件
     * @param request
     * @param fileName 文件名称
     * @return
     */
    public String uploadFile(HttpServletRequest request, String fileName) {
        this.request = request;
        try {
            File saveDirectory;
            if(StrUtil.isNotEmpty(repository)){
                saveDirectory= new File(repository);
            }else {
                saveDirectory=null;
            }
            TheFileItemFactory factory = new TheFileItemFactory(0, saveDirectory, fileName);
            //设置文件工厂
            upload.setFileItemFactory(factory);
            //上传文件
            List<FileItem> fileItems = upload.parseRequest(request);

            if (fileItems.size() > 0) {
                FileItem fileItem = fileItems.get(0);
                if (!fileItem.isFormField() && fileItem instanceof TheFileItem) {//判断是普通表单还是带文件的表单
                    TheFileItem theFileItem = (TheFileItem) fileItem;
                    String filePath = theFileItem.getStoreLocation().getPath();

                    return filePath;

                }
            }
        } catch (FileUploadException e) {
            log.error("文件上传失败:",e);
            redisUtil.putExceptionInfo(token, "文件上传失败");
        }
        return null;
    }

    public String uploadFile(HttpServletRequest request) {
        fileName=UUID.randomUUID().toString().replaceAll("-", "");
        return uploadFile(request,fileName);
    }
}
