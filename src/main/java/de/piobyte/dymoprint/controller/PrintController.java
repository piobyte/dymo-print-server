/*
 * Copyright (C) 2021 piobyte GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.piobyte.dymoprint.controller;

import de.piobyte.dymoprint.controller.exception.BadRequestException;
import de.piobyte.dymoprint.controller.exception.InternalServerErrorException;
import de.piobyte.dymoprint.controller.exception.ResourceNotFoundException;
import de.piobyte.dymoprint.printer.Tape;
import de.piobyte.dymoprint.service.print.InvalidParameterException;
import de.piobyte.dymoprint.service.print.PrintService;
import de.piobyte.dymoprint.service.print.Printer;
import de.piobyte.dymoprint.service.print.PrinterNotFoundException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import javax.imageio.ImageIO;

@Slf4j
@RestController
@RequestMapping("/printers")
@RequiredArgsConstructor
public class PrintController {

    private final PrintService printService;

    @Operation(summary = "Get attached printers.")
    @ApiResponses({
            @ApiResponse(responseCode = "200")
    })
    @GetMapping
    public List<Printer> getPrinters() {
        return printService.listAvailablePrinters();
    }

    @Operation(summary = "Get printer by serial number.")
    @ApiResponses({
            @ApiResponse(responseCode = "200"),
            @ApiResponse(responseCode = "404", description = "Not found", content = @Content)
    })
    @GetMapping(value = "/{serialNumber}")
    public Printer getPrinter(@PathVariable String serialNumber) {
        return searchForPrinter(serialNumber).orElseThrow(ResourceNotFoundException::new);
    }

    @Operation(summary = "Print label (PNG, JPEG, BPM, GIF).", description = "The uploaded image should have the respective pixel height of the tape." +
            "If the height of the uploaded image is greater " +
            "than the height of the respective band, the image will be scaled down. \n" +
            "     <pre>\n" +
            "     DYMO LabelManager PnP\n" +
            "     +------------------+--------------+\n" +
            "     |  Label cassette  | Image height |\n" +
            "     +------------------+--------------+\n" +
            "     | D1 6mm  | 1/4 in | 32 pixel     |\n" +
            "     | D1 9mm  | 3/8 in | 48 pixel     |\n" +
            "     | D1 12mm | 1/2 in | 64 pixel     |\n" +
            "     +------------------+--------------+\n" +
            "     </pre>")
    @ApiResponses({
            @ApiResponse(responseCode = "200"),
            @ApiResponse(responseCode = "400",
                    description = "Bad request",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Printer not found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal error", content = @Content)
    })
    @PostMapping(value = "/{serialNumber}", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE}, produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity printLabel(@RequestPart MultipartFile multipartFile, @PathVariable String serialNumber,
                                     @RequestParam(defaultValue = "D1_12_MM") Tape tape, @RequestParam(required = false, defaultValue = "false") boolean preview) {
        Printer printer = searchForPrinter(serialNumber).orElseThrow(ResourceNotFoundException::new);
        var supportedLabelHeights = printer.getLabelHeight();

        if (!supportedLabelHeights.containsKey(tape)) {
            throw new BadRequestException("Unsupported tape! " + tape);
        }
        int maxLabelHeight = printer.getLabelHeight().get(tape);

        try {
            BufferedImage image = ImageIO.read(multipartFile.getInputStream());
            if (image == null) {
                throw new BadRequestException("Unsupported image type!");
            }
            if (image.getHeight() != maxLabelHeight) {
                image = scaleLabel(image, maxLabelHeight);
            }

            // print
            if (!preview) {
                printService.printLabel(printer.getSerialNumber(), tape, image);
            }

            // prepare preview
            byte[] imageData = getPngImage(image);

            return ResponseEntity
                    .ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .contentLength(imageData.length)
                    .body(imageData);
        } catch (IOException e) {
            throw new InternalServerErrorException(e.getMessage());
        } catch (InvalidParameterException e) {
            throw new BadRequestException(e.getMessage());
        } catch (PrinterNotFoundException e) {
            throw new ResourceNotFoundException();
        }
    }

    private Optional<Printer> searchForPrinter(String serialNumber) {
        return printService.listAvailablePrinters()
                .stream()
                .filter(printer -> serialNumber != null && serialNumber.equals(printer.getSerialNumber()))
                .findFirst();
    }

    private BufferedImage scaleLabel(BufferedImage originalImage, int targetHeight) {
        float scaleFactor = (1f * targetHeight) / originalImage.getHeight();
        int targetWidth = (int) (originalImage.getWidth() * scaleFactor);
        log.info("Need to scale image. original: {}x{} scaled: {}x{}", originalImage.getHeight(),
                originalImage.getWidth(), targetHeight, targetWidth);
        var tmpImage = originalImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);

        BufferedImage scaledBufferedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g2d = scaledBufferedImage.createGraphics();
        g2d.drawImage(tmpImage, 0, 0, null);
        g2d.dispose();

        return scaledBufferedImage;
    }

    private byte[] getPngImage(BufferedImage image) throws IOException {
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bao);
        return bao.toByteArray();
    }
}
