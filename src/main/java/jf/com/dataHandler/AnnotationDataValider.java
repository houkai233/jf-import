package jf.com.dataHandler;

import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;

/**
 * 采用javax.validation模块
 * 注解校验类
 * @param <T>
 */
@Validated
public interface AnnotationDataValider<T extends ValidedData>{

    void dataValid(@Valid T model);
}
@Service
class AnnotationDataValiderImpl<T extends ValidedData> implements AnnotationDataValider<T> {

    @Override
    public void dataValid(@Valid T model) {

    }
}

