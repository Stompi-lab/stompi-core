package tech.stompi.wallet;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.util.HashSet;
import java.util.Set;

/** Strict local QR parser. No URI is opened and no payment is sent by scanning. */
final class PaymentRequest {
    final String address, amount, label;
    private PaymentRequest(String address, String amount, String label) {
        this.address=address; this.amount=amount; this.label=label;
    }
    static PaymentRequest parse(String raw) throws Exception {
        if(raw==null || raw.length()>2048) throw new IllegalArgumentException("Invalid QR");
        raw=raw.trim();
        String address=raw, amount="", label="";
        if(raw.startsWith("stompi:")) {
            String payload=raw.substring(7);
            if(payload.contains("#") || payload.startsWith("//")) throw new IllegalArgumentException("Invalid URI");
            int split=payload.indexOf('?');
            address=split<0?payload:payload.substring(0,split);
            Set<String> seen=new HashSet<>();
            if(split>=0) for(String part:payload.substring(split+1).split("&")) {
                String[] pair=part.split("=",2);
                String key=URLDecoder.decode(pair[0],"UTF-8");
                String value=pair.length==2?URLDecoder.decode(pair[1],"UTF-8"):"";
                if(!seen.add(key) || key.startsWith("req-")) throw new IllegalArgumentException("Unsupported parameter");
                if(key.equals("amount")) {
                    if(!value.matches("[0-9]+(\\.[0-9]{1,8})?")) throw new IllegalArgumentException("Invalid amount");
                    BigDecimal number=new BigDecimal(value);
                    if(number.signum()<=0 || number.compareTo(new BigDecimal("21000000"))>0) throw new IllegalArgumentException("Invalid amount");
                    amount=number.stripTrailingZeros().toPlainString();
                } else if(key.equals("label")) {
                    if(value.length()>80 || value.chars().anyMatch(c->Character.isISOControl(c))) throw new IllegalArgumentException("Invalid label");
                    label=value;
                } else throw new IllegalArgumentException("Unsupported parameter");
            }
        }
        if(!StompiAddress.isNativeSegwitV0(address)) throw new IllegalArgumentException("Invalid Stompi address");
        return new PaymentRequest(address,amount,label);
    }
}
