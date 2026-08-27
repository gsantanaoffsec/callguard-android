package br.dev.callguard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneOriginTest {

    @Test
    fun `celular de sao paulo e reconhecido`() {
        val origem = PhoneOrigin.of("+5511999998888")
        assertEquals("Brasil", origem.country)
        assertEquals("11", origem.areaCode)
        assertEquals("São Paulo · SP", origem.region)
        assertEquals(PhoneOrigin.LineType.MOBILE, origem.lineType)
    }

    @Test
    fun `fixo e distinguido de celular pelo primeiro digito`() {
        assertEquals(
            PhoneOrigin.LineType.LANDLINE,
            PhoneOrigin.of("+551133334444").lineType,
        )
        assertEquals(
            PhoneOrigin.LineType.MOBILE,
            PhoneOrigin.of("+5511999998888").lineType,
        )
    }

    @Test
    fun `ddds de varias regioes`() {
        assertEquals("Rio de Janeiro · RJ", PhoneOrigin.of("+5521999998888").region)
        assertEquals("Brasília · DF", PhoneOrigin.of("+5561999998888").region)
        assertEquals("Salvador · BA", PhoneOrigin.of("+5571999998888").region)
        assertEquals("Manaus · AM", PhoneOrigin.of("+5592999998888").region)
        assertEquals("Porto Alegre · RS", PhoneOrigin.of("+5551999998888").region)
    }

    @Test
    fun `ddd inexistente nao inventa regiao`() {
        // 20, 23, 25, 26, 29, 30, 36, 39, 40, 50, 52, 56-60, 70, 72, 76, 78, 80, 90
        // nao existem no plano da ANATEL.
        val origem = PhoneOrigin.of("+5520999998888")
        assertEquals("20", origem.areaCode)
        assertNull("Nao pode inventar uma regiao", origem.region)
    }

    @Test
    fun `numeros de servico sao classificados`() {
        assertEquals(PhoneOrigin.LineType.TOLL_FREE, PhoneOrigin.of("08000000000").lineType)
        assertEquals(PhoneOrigin.LineType.SHARED_COST, PhoneOrigin.of("03001234567").lineType)
        assertEquals(PhoneOrigin.LineType.SPECIAL_SERVICE, PhoneOrigin.of("40041234").lineType)
    }

    @Test
    fun `numeros curtos sao reconhecidos`() {
        assertEquals(PhoneOrigin.LineType.SHORT_CODE, PhoneOrigin.of("190").lineType)
        assertEquals(PhoneOrigin.LineType.SHORT_CODE, PhoneOrigin.of("1746").lineType)
    }

    @Test
    fun `numero estrangeiro nao vira brasileiro`() {
        val origem = PhoneOrigin.of("+14155552671")
        assertEquals("Exterior", origem.country)
        assertEquals(PhoneOrigin.LineType.INTERNATIONAL, origem.lineType)
        assertNull(origem.region)
    }

    @Test
    fun `numero ausente nao quebra`() {
        val origem = PhoneOrigin.of(null)
        assertEquals(PhoneOrigin.LineType.UNKNOWN, origem.lineType)
        assertNull(origem.country)
        assertNull(origem.region)
    }

    @Test
    fun `numero nacional sem mais e tratado como brasileiro`() {
        val origem = PhoneOrigin.of("11999998888")
        assertEquals("São Paulo · SP", origem.region)
        assertEquals(PhoneOrigin.LineType.MOBILE, origem.lineType)
    }

    @Test
    fun `descricao junta regiao e tipo`() {
        assertEquals("São Paulo · SP · celular", PhoneOrigin.of("+5511999998888").describe())
        assertTrue(PhoneOrigin.of("+14155552671").describe().contains("internacional"))
    }

    @Test
    fun `todos os 67 ddds do plano da ANATEL respondem`() {
        val ddds = listOf(
            11, 12, 13, 14, 15, 16, 17, 18, 19,
            21, 22, 24, 27, 28,
            31, 32, 33, 34, 35, 37, 38,
            41, 42, 43, 44, 45, 46, 47, 48, 49,
            51, 53, 54, 55,
            61, 62, 63, 64, 65, 66, 67, 68, 69,
            71, 73, 74, 75, 77, 79,
            81, 82, 83, 84, 85, 86, 87, 88, 89,
            91, 92, 93, 94, 95, 96, 97, 98, 99,
        )
        assertEquals("O plano da ANATEL tem 67 codigos", 67, ddds.size)
        ddds.forEach { ddd ->
            val origem = PhoneOrigin.of("+55${ddd}999998888")
            assertEquals("DDD $ddd deveria ser lido", ddd.toString(), origem.areaCode)
            assertTrue("DDD $ddd sem regiao mapeada", origem.region != null)
        }
    }
}
