package com.stdntedu.ai.extraction.service;

import com.stdntedu.ai.extraction.entity.AiExtractionTaskEntity;

public record CreatedExtraction(Long taskId, AiExtractionTaskEntity task) { }
