package com.base.admin.service;

import com.base.admin.domain.dto.SysAiProviderDTO;
import com.base.admin.domain.entity.SysAiProvider;
import com.base.admin.domain.vo.SysAiProviderVO;

import java.util.List;
import java.util.Optional;

public interface SysAiProviderService {

    List<SysAiProviderVO> listAll();

    void update(SysAiProviderDTO dto);

    /** 已启用且已配置 Key 的云厂商 */
    List<SysAiProvider> listReady();

    Optional<SysAiProvider> findReady(String provider);
}
