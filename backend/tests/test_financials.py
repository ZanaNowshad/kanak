import unittest
from decimal import Decimal

def quantize_bhd(val: Decimal) -> Decimal:
    return val.quantize(Decimal("0.001"))

def calculate_bahrain_vat_line(quantity: Decimal, unit_price_gross: Decimal, vat_rate: Decimal = Decimal("0.100")):
    line_gross = quantize_bhd(quantity * unit_price_gross)
    one_plus_rate = Decimal("1.000") + vat_rate
    line_net = quantize_bhd(line_gross / one_plus_rate)
    line_vat = line_gross - line_net
    return {
        "gross": line_gross,
        "net": line_net,
        "vat": line_vat
    }

class TestBahrainFinancials(unittest.TestCase):
    def test_bahrain_vat_standard_calculation(self):
        # Example: 2 units at BHD 1.100 VAT-inclusive
        # Total Gross: 2.200, Net: 2.000, VAT: 0.200
        res = calculate_bahrain_vat_line(Decimal("2.000"), Decimal("1.100"), Decimal("0.100"))
        self.assertEqual(res["gross"], Decimal("2.200"))
        self.assertEqual(res["net"], Decimal("2.000"))
        self.assertEqual(res["vat"], Decimal("0.200"))
        self.assertEqual(res["net"] + res["vat"], res["gross"])

    def test_bahrain_vat_zero_rated(self):
        # Zero-rated staple goods (e.g. fresh bread, plain milk)
        res = calculate_bahrain_vat_line(Decimal("5.000"), Decimal("1.100"), Decimal("0.000"))
        self.assertEqual(res["gross"], Decimal("5.500"))
        self.assertEqual(res["net"], Decimal("5.500"))
        self.assertEqual(res["vat"], Decimal("0.000"))

    def test_multi_line_invoice_reconciliation(self):
        line1 = calculate_bahrain_vat_line(Decimal("3.000"), Decimal("1.250"), Decimal("0.100"))
        line2 = calculate_bahrain_vat_line(Decimal("2.000"), Decimal("0.800"), Decimal("0.000"))

        total_gross = line1["gross"] + line2["gross"]
        total_net = line1["net"] + line2["net"]
        total_vat = line1["vat"] + line2["vat"]

        self.assertEqual(total_net + total_vat, total_gross)
        self.assertEqual(total_gross, Decimal("5.350"))

if __name__ == "__main__":
    unittest.main()
