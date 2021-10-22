package jf.com.fileUploadHandler;

import lombok.SneakyThrows;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.io.FileCleaningTracker;

import java.io.File;
import java.util.UUID;

public class TheFileItemFactory extends DiskFileItemFactory {
    //文件目录路径
    private final File repository;
    //文件名称
    private final String newFileName;
    //当上传的文件大小大于缓冲区的时候，将它放到临时文件中
    private int sizeThreshold;

    public TheFileItemFactory() {
        this(10240, null, UUID.randomUUID().toString().replaceAll("-", ""));
    }

    public TheFileItemFactory(int sizeThreshold, File repository,String newFileName) {
        super(sizeThreshold, repository);
        this.sizeThreshold = 10240;
        this.sizeThreshold = sizeThreshold;
        this.repository = repository;
        this.newFileName = newFileName;
    }

    @SneakyThrows
    @Override
    public FileItem createItem(String fieldName, String contentType, boolean isFormField, String fileName) {
        TheFileItem result = new TheFileItem(fieldName, contentType, isFormField, fileName, this.sizeThreshold, this.repository,this.newFileName);
        FileCleaningTracker tracker = this.getFileCleaningTracker();
        if (tracker != null) {
            tracker.track(result.getTempFile(), result);
        }

        return result;
    }
}
