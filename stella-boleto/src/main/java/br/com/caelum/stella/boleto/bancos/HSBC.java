package br.com.caelum.stella.boleto.bancos;

import java.net.URL;
import java.util.Calendar;

import br.com.caelum.stella.boleto.Banco;
import br.com.caelum.stella.boleto.Boleto;
import br.com.caelum.stella.boleto.Emissor;

/**
 * @author Alberto Pc
 */
public class HSBC extends AbstractBanco implements Banco {
	/*
	 * codigo cedente - Codigo do cedente fornecido pela empresa. - Campo numero
	 * do documento - CNR(ate 13 posicoes) ou CNRFacil(ate 6 posicoes) - Campo
	 * data de processamente nao preencher(sem registro) - Campo nosso
	 * numero/codigo do documento - Nosso numero + 3 digitos
	 * verificadores(1->modulo 11,2->tipoIdentificador,3->modulo 11) - Campo
	 * carteira - Inserir CNR - Campo unidade cedente - Codigo e nome da agencia
	 * cedente - Campo sacado - Linha 1 -> Nome e CPF/CNPJ, Linha2 -> Endereco
	 * do sacado, Linha3 -> Cep,Distrito... - Campo carteira - inserir CNR
	 * 
	 * 
	 * OBS: Data de processamento deve ser nulo(nessa sem registro)
	 */
	public static final String LOCAL_PAGAMENTO = "Pagar preferencialmente em agência do HSBC";
	private static final String NUMERO_HSBC = "399";
	private static final String DIGITO_HSBC = "9";
	private static final String CODIGO_APLICATIVO = "2";

	private int getSegundoDigitoVerificador(Boleto boleto) {
		/*
		 * estou deixando todo mundo com o mesmo numero de digitos do codigo
		 * para facilitar no calculo.
		 */

		int tipo = getTipoIdentificador(boleto);
		long codigoDoDocumento = Long.parseLong(getNossoNumeroDoEmissorFormatado(boleto.getEmissor()));
		long codigo = Long.valueOf("" + codigoDoDocumento + getPrimeiroDigitoVerificador(boleto) + tipo);
		int codigoDoCedente = boleto.getEmissor().getCodigoFornecidoPelaAgencia();

		int resultado;
		String soma;
		if (tipo == 4) {

			int data = Integer.valueOf(String.format("%1$td%1$tm%1$ty", boleto.getDatas().getVencimento()));
			soma = String.format("%010d", codigo + codigoDoCedente + data);
		} else {
			soma = String.format("%010d", codigo + codigoDoCedente);
		}
		resultado = 0;
		int i = 0;
		for (int x : new int[] { 8, 9, 2, 3, 4, 5, 6, 7, 8, 9 }) {
			int parcela = x * (soma.charAt(i) - '0');
			resultado += parcela;
			i++;
		}
		resultado %= 11;
		resultado %= 10;
		return resultado;

	}

	@Override
	public String getNumeroFormatadoComDigito() {
		return NUMERO_HSBC + "-" + DIGITO_HSBC;
	}

	public String getCodigoDoDocumentoFinalComDigitosVerificadores(Boleto boleto) {
		return String.format("%s%d%d%d", boleto.getNumeroDoDocumento(), getPrimeiroDigitoVerificador(boleto),
				getTipoIdentificador(boleto), getSegundoDigitoVerificador(boleto));
	}

	private int getPrimeiroDigitoVerificador(Boleto boleto) {
		return dvGenerator.geraDigitoMod11(getNossoNumeroDoEmissorFormatado(boleto.getEmissor()));
	}

	@Override
	public String geraCodigoDeBarrasPara(Boleto boleto) {
		StringBuilder digitosVerificadores = new StringBuilder();
		int primeiroDigitoVerificador = getPrimeiroDigitoVerificador(boleto);
		int tipo = getTipoIdentificador(boleto);
		int segundoDigitoVerificador = getSegundoDigitoVerificador(boleto);
		digitosVerificadores.append(primeiroDigitoVerificador);
		digitosVerificadores.append(tipo);
		digitosVerificadores.append(segundoDigitoVerificador);

		StringBuilder codigoDeBarras = new StringBuilder();
		codigoDeBarras.append(getNumeroFormatado());
		codigoDeBarras.append(boleto.getCodigoEspecieMoeda());
		codigoDeBarras.append(boleto.getFatorVencimento());
		codigoDeBarras.append(boleto.getValorFormatado());
		/*
		 * codigo do cedente
		 */
		codigoDeBarras.append(String.format("%07d", boleto.getEmissor().getCodigoFornecidoPelaAgencia()));
		/*
		 * codigo do documento.
		 */
		codigoDeBarras.append(getNossoNumeroDoEmissorFormatado(boleto.getEmissor()));

		/*
		 * não sei o que é retorno de tres digitos
		 */

		codigoDeBarras.append(getDataFormatoJuliano(boleto.getDatas().getVencimento(), tipo));
		codigoDeBarras.append(HSBC.CODIGO_APLICATIVO);
		codigoDeBarras.insert(4, dvGenerator.geraDigitoMod11(codigoDeBarras.toString()));
		return codigoDeBarras.toString();
	}

	private int getTipoIdentificador(Boleto boleto) {
		int tipo = 4;
		if (boleto.getDatas().getVencimento() == null
				|| (boleto.getFatorVencimento() == null || boleto.getFatorVencimento().trim().equals(""))) {
			// tipo = 5;
			throw new IllegalArgumentException("Boletos do HSBC sem data de vencimento não são suportados.");
		}
		return tipo;
	}

	/**
	 * @param vencimento
	 *            vencimento do boleto
	 * @param tipo
	 *            tipo do identificador, 4 ou 5
	 * @return data no formato Juliano
	 */
	public String getDataFormatoJuliano(Calendar vencimento, int tipo) {
		/*
		 * aqui tem outra verificação, que é o tal de um retorno de 3 dígitos
		 * que ainda não identifiquei. Por enquanto, é tipo 4 eu estou
		 * calculando.
		 */
		String result;
		Calendar dataLimite = Calendar.getInstance();
		dataLimite.set(Calendar.DAY_OF_MONTH, 1);
		dataLimite.set(Calendar.MONTH, 7 - 1);
		dataLimite.set(Calendar.YEAR, 1997);
		if (vencimento.before(dataLimite)) {
			result = "0000";
		} else {
			if (tipo == 4) {
				/*
				 * http://java.sun.com/j2se/1.4.2/docs/api/java/util/
				 * GregorianCalendar.html
				 * 
				 * The only difference between the Gregorian and the Julian
				 * calendar is the leap year rule. The Julian calendar specifies
				 * leap years every four years, whereas the Gregorian calendar
				 * omits century years which are not divisible by 400.
				 * 
				 * A linha abaixo não funcionará corretamente apenas no ano de
				 * 2100.
				 */
				int diaDoAno = vencimento.get(Calendar.DAY_OF_YEAR);
				int digitoDoAno = vencimento.get(Calendar.YEAR) % 10;
				result = String.format("%03d%d", diaDoAno, digitoDoAno);

			} else if (tipo == 5) {
				result = "0000";
			} else {
				throw new IllegalArgumentException("Tipo inválido");
			}
		}
		return result;
	}

	@Override
	public String getCarteiraDoEmissorFormatado(Emissor emissor) {
		return "CNR";
	}

	@Override
	public String getContaCorrenteDoEmissorFormatado(Emissor emissor) {
		return String.format("%07d", emissor.getContaCorrente());
	}

	@Override
	public URL getImage() {
		return getClass().getResource(String.format("/br/com/caelum/stella/boleto/img/%s.png", getNumeroFormatado()));
	}

	@Override
	public String getNossoNumeroDoEmissorFormatado(Emissor emissor) {
		return String.format("%013d", emissor.getNossoNumero());
	}

	@Override
	public String getNumeroFormatado() {
		return HSBC.NUMERO_HSBC;
	}

	@Override
	public String getAgenciaECodigoCedente(Emissor emissor) {
		return String.format("%07d", emissor.getCodigoFornecidoPelaAgencia());
	}

}
