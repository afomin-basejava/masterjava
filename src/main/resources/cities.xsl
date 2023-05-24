<xsl:transform version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
               xmlns:Payload="http://javaops.ru">
    <xsl:output method="html" omit-xml-declaration="yes" indent="no"/>
    <xsl:param name="topjava"/>
    <xsl:strip-space elements="*"/>
    <xsl:template match="/*[name()='Payload']/*[name()='Users']/*[name()='User']">
        <xsl:copy-of select="."/>
        <xsl:text>&#xa;</xsl:text><!-- put in the newline -->
    </xsl:template>
    <xsl:template match="text()"/>
</xsl:transform>