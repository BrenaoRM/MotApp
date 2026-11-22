package com.example.financacelular.data

import java.time.format.DateTimeFormatter

// DateTimeFormatter é imutável e thread-safe, então pode ser compartilhado.
internal val FORMATO_ANO_MES: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
internal val FORMATO_DATA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
internal val FORMATO_DIA_MES: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")
internal val FORMATO_DATA_CURTA: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yy")
