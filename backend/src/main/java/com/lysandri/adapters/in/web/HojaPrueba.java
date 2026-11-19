package com.lysandri.adapters.in.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class HojaPrueba {
    private HojaPrueba() {}

    static byte[] generar(List<Map<String,Object>> filas) {
        List<String> columnas=List.of("empresa","oc","titulo","cohorte","cupos","moneda","bruto","descuento","base",
                "impuesto","total","condicion","vence","estado","estado_pago","comprobante","fecha");
        StringBuilder hoja=new StringBuilder("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData><row>");
        columnas.forEach(c -> celda(hoja,c.replace('_',' ')));
        hoja.append("</row>");
        for (var fila : filas) {
            hoja.append("<row>");
            columnas.forEach(c -> celda(hoja,fila.get(c)));
            hoja.append("</row>");
        }
        hoja.append("</sheetData></worksheet>");
        try {
            var resultado=new ByteArrayOutputStream();
            try (var zip=new ZipOutputStream(resultado,StandardCharsets.UTF_8)) {
                archivo(zip,"[Content_Types].xml","""
                        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                          <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                          <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                        </Types>
                        """);
                archivo(zip,"_rels/.rels","""
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                        </Relationships>
                        """);
                archivo(zip,"xl/workbook.xml","""
                        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                          <sheets><sheet name="Ordenes de prueba" sheetId="1" r:id="rId1"/></sheets>
                        </workbook>
                        """);
                archivo(zip,"xl/_rels/workbook.xml.rels","""
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                        </Relationships>
                        """);
                archivo(zip,"xl/worksheets/sheet1.xml",hoja.toString());
            }
            return resultado.toByteArray();
        } catch (IOException fallo) {throw new UncheckedIOException(fallo);}
    }

    private static void celda(StringBuilder hoja,Object valor) {
        if (valor instanceof Number) {
            hoja.append("<c><v>").append(valor).append("</v></c>");
        } else {
            String texto=valor==null ? "" : valor.toString().replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\uFFFE\\uFFFF]","");
            hoja.append("<c t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    .append(texto.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")).append("</t></is></c>");
        }
    }

    private static void archivo(ZipOutputStream zip,String nombre,String contenido) throws IOException {
        zip.putNextEntry(new ZipEntry(nombre));
        zip.write(contenido.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
