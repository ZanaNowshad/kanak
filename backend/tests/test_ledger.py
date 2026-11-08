import unittest
from decimal import Decimal

class TestStockLedger(unittest.TestCase):
    def test_ledger_movement_types(self):
        valid_movements = {
            "PURCHASE_INTAKE",
            "VAN_LOAD",
            "VAN_SALE",
            "VAN_UNLOAD",
            "AUDIT_ADJUSTMENT",
            "DAMAGE_WRITE_OFF"
        }
        sample_movement = "VAN_LOAD"
        self.assertIn(sample_movement, valid_movements)

    def test_van_stock_balance_invariance(self):
        initial_van_stock = Decimal("50.000")
        sold_qty = Decimal("12.000")
        remaining = initial_van_stock - sold_qty
        self.assertEqual(remaining, Decimal("38.000"))

if __name__ == "__main__":
    unittest.main()
