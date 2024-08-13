package com.ghostchu.btn.sparkle.spring.controller;

import com.ghostchu.btn.sparkle.exception.RequestPageSizeTooLargeException;
import com.ghostchu.btn.sparkle.util.ServletUtil;
import jakarta.servlet.http.HttpServletRequest;

public abstract class SparkleController {

    public String ua(HttpServletRequest req){
        return req.getHeader("User-Agent");
    }

    public String ip(HttpServletRequest req){
        return ServletUtil.getIP(req);
    }

    public Paging paging(Integer page, Integer pageSize) throws RequestPageSizeTooLargeException {
        if (page == null) page = 0;
        if (pageSize == null) pageSize = 100;
        if (pageSize > 3000) {
            throw new RequestPageSizeTooLargeException();
        }
        return new Paging(page,pageSize);
    }


    public record Paging( int page,int pageSize){
    }
}
