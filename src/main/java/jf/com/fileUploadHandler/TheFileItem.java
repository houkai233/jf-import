package jf.com.fileUploadHandler;

import cn.hutool.core.util.ArrayUtil;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileNotFoundException;

public class TheFileItem extends DiskFileItem {
    private final File repository;
    private final String newFileName;
    private String fileExtension;

    /**
     * 允许上传的扩展名
     */
    private final String[] extensionPermit = {"dbf", "xls", "xlsx", "zip"};

    public TheFileItem(String fieldName, String contentType, boolean isFormField, String fileName, int sizeThreshold, File repository, String newFileName) throws FileNotFoundException {
        super(fieldName, contentType, isFormField, fileName, sizeThreshold, repository);
        this.repository = repository;
        this.newFileName = newFileName;

        //拿到文件的名字
        if (fileName == null || fileName.trim().equals("")) {
            //文件名为空
            throw new FileNotFoundException("文件名为空");
        }
        fileExtension = FilenameUtils.getExtension(fileName);
        if (!ArrayUtil.containsIgnoreCase(extensionPermit, fileExtension)) {
            throw new UnsupportedOperationException("不支持的文件类型");
        }
    }

    @Override
    protected File getTempFile() {
        File tempDir = this.repository;
        if (tempDir == null) {
            tempDir = new File(System.getProperty("java.io.tmpdir"));
        }
        //生成文件名
        String fileName = newFileName;
        if (!newFileName.endsWith(fileExtension)) {
            fileName += "." + fileExtension;
        }
        return new File(tempDir, fileName);
    }

    //不重写此方法linux环境下会自动删除上传文件
    protected void finalize() {}
}
