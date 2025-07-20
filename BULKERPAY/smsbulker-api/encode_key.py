import base64
import json

# Replace with the actual path to your service account JSON file
file_path = r"C:\Users\User\Downloads\smsbulker-99-firebase-adminsdk-hrmyo-a0411848dc.json"

try:
    with open(file_path, 'r', encoding='utf-8') as f:
        service_account_json = f.read()

    # Encode the JSON string to Base64
    encoded_string = base64.b64encode(service_account_json.encode('utf-8')).decode('utf-8')

    print("Base64 Encoded Key:")
    print(encoded_string)

except FileNotFoundError:
    print(f"Error: File not found at {file_path}")
except Exception as e:
    print(f"An error occurred: {e}")