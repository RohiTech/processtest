package org.rohitech.process;

import org.compiere.model.MBPartner;
import org.compiere.model.MBPartnerLocation;
import org.compiere.model.MInvoice;
import org.compiere.model.MInvoiceLine;
import org.compiere.model.MPriceList;
import org.compiere.model.MUser;
import org.compiere.process.DocAction;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Date;
import java.util.logging.Level;

import javax.sql.RowSet;

import org.compiere.util.DB;
import org.compiere.util.Env;

public class GenerateInvoice extends SvrProcess
{
	// Parametro
	private int p_period_id = 0;

	@Override
	protected void prepare() {
		// TODO Auto-generated method stub
		ProcessInfoParameter[] param = getParameter();
		
		for(int i = 0; i < param.length; i++)
		{
			String name = param[i].getParameterName();
			
			if(param[i].getParameter() == null)
				;
			else if (name.equals("pa_period_id"))
				p_period_id = ((BigDecimal) param[i].getParameter()).intValue();
			else
				log.log(Level.SEVERE, "Parametro Desconocido: " + name);
		}
	}

	@Override
	protected String doIt() throws Exception {
		// TODO Auto-generated method stub
		
		// Obtenemos el valor de la variable de contexto de usuario actual
		int v_user_id = Env.getAD_User_ID(Env.getCtx());
		
		int v_tercero_id = 0, v_contact_id = 0, v_location_id = 0;
		
		MUser usuario = MUser.get(v_user_id);
		
		// Consulta SQL listar los terceros que son clientes
		String query = "select\r\n"
				+ "	cb.c_bpartner_id,\r\n"
				+ "	u.ad_user_id,\r\n"
				+ "	l.c_bpartner_location_id \r\n"
				+ "from c_bpartner cb\r\n"
				+ "	join c_bpartner_location l\r\n"
				+ "	on cb.c_bpartner_id  = l.c_bpartner_id \r\n"
				+ "	join ad_user u\r\n"
				+ "	on u.c_bpartner_id = cb.c_bpartner_id \r\n"
				+ "where cb.ad_client_id = 1000000\r\n"
				+ "	and cb.isactive = 'Y'\r\n"
				+ "	and cb.iscustomer = 'Y'\r\n"
				+ "	and not exists\r\n"
				+ "	(\r\n"
				+ "		select 1 from c_invoice i\r\n"
				+ "		where i.c_bpartner_id = cb.c_bpartner_id\r\n"
				+ "			and extract(month from i.dateacct::date) = 6\r\n"
				+ "			and extract(year from i.dateacct::date) = 2021\r\n"
				+ "	);";
		
		//System.out.println("Consulta SQL: " + query);
		
		//System.out.println("ID Usuario Actual: " + String.valueOf(v_user_id));
		//System.out.println("Nombre del Usuario Actual: " + usuario.getName());
		
		RowSet rs = DB.getRowSet(query);
		
		Date hoy = new Date();
		
		while(rs.next())
		{
			// Capturar los valores de cada campo por cada fila resultante de la consulta
			v_tercero_id = rs.getInt(1);
			v_contact_id =  rs.getInt(2);
			v_location_id = rs.getInt(3);
			
			MBPartner bp = new MBPartner(Env.getCtx(), v_tercero_id, this.get_TrxName());
			
			MBPartnerLocation bl = new MBPartnerLocation(bp);
			
			// Crear el encabezado de la factura -- INICIO
			MInvoice inv = new MInvoice(Env.getCtx(), 0, this.get_TrxName());
			
			inv.setAD_Org_ID(1000000);
			inv.setIsSOTrx(true);
			inv.setDescription("MENSUALIDAD DE JUNIO " + bp.getName());
			inv.setDateInvoiced(new Timestamp(hoy.getTime()));
			inv.setDateAcct(new Timestamp(hoy.getTime()));
			inv.setC_DocTypeTarget_ID(1000002);
			inv.setC_BPartner_ID(v_tercero_id);
			inv.setC_BPartner_Location_ID(v_location_id);
			
			MPriceList pl = new MPriceList(Env.getCtx(), 1000003, this.get_TrxName());
			
			inv.setM_PriceList_ID(pl.getM_PriceList_ID());
			inv.setC_Currency_ID(pl.getC_Currency_ID());
			
			MUser contacto = new MUser(bp);
			
			inv.setAD_User_ID(v_contact_id);
			inv.setPaymentRule("P");
			inv.setC_PaymentTerm_ID(1000000);
			inv.setC_DocType_ID(1000002);
			
			if(!inv.save())
				throw new IllegalArgumentException("La Factura no puede ser Grabada");
			
			// Crear el encabezado de la factura -- FIN
			
			// Crear el detalle de la factura -- INICIO
			MInvoiceLine il = new MInvoiceLine(inv);
			
			il.setAD_Org_ID(1000000);
			il.setC_Invoice_ID(inv.getC_Invoice_ID());
			il.setC_Charge_ID(1000010);
			il.setQtyEntered(new BigDecimal(1));
			il.setQtyInvoiced(new BigDecimal(1));
			il.setC_UOM_ID(100);
			il.setPriceEntered(new BigDecimal(500.00));
			il.setPriceActual(new BigDecimal(500.00));
			il.setPriceList(BigDecimal.ZERO);
			il.setC_Tax_ID(1000000);
			il.setLineTotalAmt(new BigDecimal(500.00));
			
			if(!il.save())
				throw new IllegalArgumentException("El Detalle no puede ser Grabado");
			
			// Crear el detalle de la factura -- FIN
			
			inv.processIt(DocAction.ACTION_Complete);
			
			inv.saveEx();
			
			addLog(inv.getC_Invoice_ID(), inv.getDateAcct(), null, inv.getDocumentNo(), inv.get_Table_ID(), inv.getC_Invoice_ID());
		}
		
		String mensaje = "************  FACTURAS GENERADAS SATISFACTORIAMENTE **************";
		
		return mensaje;
	}
	
}
