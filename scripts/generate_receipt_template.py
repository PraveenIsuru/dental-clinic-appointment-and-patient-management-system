from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.pagesizes import A5
from reportlab.pdfgen import canvas


output = Path("output/pdf/sunrise_receipt_template.pdf")
output.parent.mkdir(parents=True, exist_ok=True)

page_width, page_height = A5
pdf = canvas.Canvas(str(output), pagesize=A5)
pdf.setTitle("Sunrise Dental Clinic Receipt Template")

pdf.setFillColor(colors.HexColor("#062B4D"))
pdf.rect(0, page_height - 100, page_width, 100, fill=1, stroke=0)
pdf.setFillColor(colors.white)
pdf.setFont("Helvetica-Bold", 19)
pdf.drawString(32, page_height - 46, "SUNRISE DENTAL CLINIC")
pdf.setFont("Helvetica", 9)
pdf.drawString(32, page_height - 66, "PAYMENT RECEIPT")

pdf.setFillColor(colors.HexColor("#0F172A"))
pdf.setFont("Helvetica-Bold", 11)
pdf.drawString(32, page_height - 132, "Receipt details")
pdf.setStrokeColor(colors.HexColor("#E2E8F0"))
pdf.line(32, page_height - 142, page_width - 32, page_height - 142)

labels = [
    ("Bill number", "BILL-0000"),
    ("Appointment", "APT-0000"),
    ("Patient", "Patient name"),
    ("Dentist", "Dentist name"),
    ("Treatment", "Treatment name"),
    ("Payment method", "CASH / CARD"),
]
y = page_height - 170
for label, value in labels:
    pdf.setFillColor(colors.HexColor("#64748B"))
    pdf.setFont("Helvetica", 9)
    pdf.drawString(32, y, label)
    pdf.setFillColor(colors.HexColor("#0F172A"))
    pdf.setFont("Helvetica-Bold", 9)
    pdf.drawRightString(page_width - 32, y, value)
    y -= 24

pdf.setFillColor(colors.HexColor("#F8FAFC"))
pdf.roundRect(32, 90, page_width - 64, 92, 8, fill=1, stroke=0)
pdf.setFillColor(colors.HexColor("#475569"))
pdf.setFont("Helvetica", 9)
pdf.drawString(46, 152, "Consultation fee")
pdf.drawString(46, 132, "Treatment charge")
pdf.setFont("Helvetica-Bold", 12)
pdf.setFillColor(colors.HexColor("#0F766E"))
pdf.drawString(46, 106, "TOTAL PAID")
pdf.drawRightString(page_width - 46, 106, "Rs. 0.00")

pdf.setFillColor(colors.HexColor("#64748B"))
pdf.setFont("Helvetica", 8)
pdf.drawCentredString(page_width / 2, 54, "Thank you for choosing Sunrise Dental Clinic")
pdf.drawCentredString(page_width / 2, 40, "This receipt was generated electronically.")

pdf.save()
print(output.resolve())
