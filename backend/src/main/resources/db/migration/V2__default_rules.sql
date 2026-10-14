INSERT INTO validation_rules (rule_key, rule_value, description) VALUES
 ('MAX_UPLOAD_SIZE_MB',   '10',                              'Maximum accepted CSV upload size in megabytes (1-50).'),
 ('ALLOWED_CURRENCIES',   'USD,EUR,GBP,JPY,INR,CHF',         'Comma-separated ISO 4217 currency codes accepted in imports.'),
 ('ALLOWED_INSTRUMENTS',  'ES,NQ,YM,CL,NG,GC,SI,ZN,ZB,6E',   'Comma-separated instrument symbols accepted in imports (illustrative symbols).'),
 ('MAX_TRADE_QUANTITY',   '10000',                           'Maximum accepted quantity for a single trade.');
